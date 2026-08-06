# Análisis: Corrección de Reintentos y Estados (Lost Updates)

## 1. Descripción del Problema Original
Al procesar un documento a través de un cronjob (ej. Animales) que se encarga de consultar un API origen, recuperar el documento, procesarlo y actualizar su estado, se estaba perdiendo el progreso histórico (`retryCount` y el estado del documento).

**Síntomas:**
- Un documento fallaba por un error de red y se marcaba en BD con `retryCount = 1` y estado `PENDING`.
- Al siguiente ciclo, el cronjob lo volvía a capturar.
- **El error:** Al capturarlo e intentar bloquearlo (`lockDocumentForProcessing` / `updateStateAndRetry`), el adaptador de persistencia estaba construyendo un objeto "nuevo" en memoria (con `retryCount = 0` y estado quemado) y haciendo un `UPSERT` (o un UPDATE directo sin leer primero).
- Esto causaba que el documento borrara su contador de reintentos en BD, volviendo a 0, haciendo que los documentos se ciclaran infinitamente sin alcanzar nunca el `MAX_RETRIES` (3).

## 2. Origen Técnico del Bug
La falla radicaba en la implementación del método `updateStateAndRetry` y en cómo los adaptadores (`AnimalPersistenceR2dbcAdapter` y `DocumentPersistenceAdapter`) estaban manejando la actualización atómica del estado a `IN_PROGRESS` (bloqueo para procesamiento concurrente).

El código original simplemente ejecutaba:
```java
// Código fallido
return documentRepository.save(
    Document.builder()
        .id(doc.getId()) // O generado si era insert
        .state(IN_PROGRESS)
        .retryCount(0) // <--- ESTO ERA EL ERROR CRÍTICO
        .build()
);
```
Incluso si no se quemaba el 0, al no leer el documento antes, se perdía el valor guardado previamente en la base de datos por ser un objeto desatachado.

## 3. La Solución Arquitectónica (Patrón Read-Before-Update)
Para solucionar esto, implementamos el patrón de lectura atómica, asegurándonos de que en el momento del "Upsert" o de aplicar el "Lock", siempre obtengamos el estado real que tiene la fila en la Base de Datos.

### A. Para Adaptadores que extienden de `AbstractReactiveAdapterOperation`
Tuvimos que exponer el método genérico `findById` en la clase base para poder hacer la consulta reactiva:

```java
// En AbstractReactiveAdapterOperation.java
public Mono<D> findById(I id) {
    return repository.findById(id).map(this::toEntity);
}
```

### B. Aplicar en los Adaptadores de Persistencia
En los métodos de los adaptadores de persistencia (como `AnimalPersistenceR2dbcAdapter` o `DocumentPersistenceAdapter`), reescribimos la lógica del bloqueo de la siguiente manera:

```java
public Mono<Document> lockDocumentForProcessing(Document doc) {
    // 1. Buscamos el documento en BD usando el ID único de BD
    return documentRepository.findById(doc.getId())
        .flatMap(existingDoc -> {
            // 2. Extraemos el retryCount y datos vitales de la BD (LA FUENTE DE VERDAD)
            int realRetry = existingDoc.getRetryCountSafe(); 
            String realStatus = existingDoc.getStatus();
            
            // 3. Cloramos la entidad preservando el retryCount real y cambiando el estado a IN_PROGRESS
            Document lockedDoc = doc.toBuilder()
                    .id(existingDoc.getId())
                    .retryCount(realRetry)
                    .status("IN_PROGRESS")
                    .build();
            
            // 4. Guardamos la entidad bloqueada sin perder información histórica
            return documentRepository.save(lockedDoc);
        })
        .switchIfEmpty(Mono.defer(() -> {
            // 5. Flujo alterno: Si no existe, es un INSERT nuevo genuino
            Document newDoc = doc.toBuilder()
                    .retryCount(0)
                    .status("IN_PROGRESS")
                    .build();
            return documentRepository.save(newDoc);
        }));
}
```

## 4. Resultado de la Solución
1. **Conservación del Historial:** Los documentos ahora arrastran su `retryCount` de ciclo en ciclo (1 -> 2 -> 3).
2. **Topes Exactos:** Al respetarse el contador, el bloque `calculateNextState()` (que compara `currentRetry < MAX_RETRIES`) puede funcionar perfectamente y pasar un documento persistente a estado final `FAILED`.
3. **Bloqueo Seguro (Locks):** Se mantiene la capacidad de procesar en paralelo marcando el documento como `IN_PROGRESS` sin aplastar la metadata crítica generada por las ejecuciones pasadas.
