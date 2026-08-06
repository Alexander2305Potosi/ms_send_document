# Resumen de Cambios de Lógica y Arquitectura

Este documento detalla los 4 grandes cambios de lógica y arquitectura implementados para estabilizar el sistema de procesamiento de documentos, habilitando el patrón Strategy y resolviendo fallas de concurrencia y tolerancia a fallos en flujos reactivos.

---

### 1. Patrón Strategy para Enrutamiento Dinámico

**❌ El Problema (Antes):** 
Existían rutas quemadas en el código (`/products/daily/animal`, `/products/daily/document`) y un `ProductHandler` que usaba sentencias `if` o métodos distintos que repetían toda la lógica para invocar casos de uso diferentes.

**✅ La Solución (Implementada):**
Se creó una clase abstracta padre y se utilizó una colección (Lista/Mapa) inyectada por Spring. Al usar el endpoint unificado `/products/{processor}`, se busca la implementación dinámicamente.

```java
// 1. La clase abstracta padre o Interfaz
public abstract class AbstractDocumentProcessingUseCase<T, H> {
    // Cada hijo dice cómo se llama (ej. "SOAP", "Animal")
    protected abstract String implementationName(); 
    
    // Método principal común para todos
    public Mono<FileUploadResponse> processDocument(T doc) { ... }
}

// 2. En el Handler, inyectamos TODOS los hijos automáticamente
@Component
public class ProductHandler {
    private final List<AbstractDocumentProcessingUseCase<?, ?>> useCases;

    public ProductHandler(List<AbstractDocumentProcessingUseCase<?, ?>> useCases) {
        this.useCases = useCases;
    }

    public Mono<ServerResponse> handleProcess(ServerRequest request) {
        String processorPath = request.pathVariable("processor"); // "animal" o "soap"
        
        // El Strategy: Buscamos la implementación correcta en tiempo de ejecución
        var targetUseCase = useCases.stream()
            .filter(uc -> uc.implementationName().equalsIgnoreCase(processorPath))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Processor no soportado"));

        return targetUseCase.processDocument(...)
            .flatMap(response -> ServerResponse.ok().bodyValue(response));
    }
}
```

---

### 2. Sincronización Atómica del Contador de Reintentos (Base de datos)

**❌ El Problema (Antes):**
El servicio leía el documento desde un API simulado que no tenía contexto de la base de datos, llegando siempre con `retry_count = 0`. Al bloquearlo en BD, se machacaba el valor real perdiendo el histórico de fallos, creando un bucle infinito en estado `PENDING`.

```java
// CÓDIGO ERRÓNEO ANTERIOR
.flatMap(entity -> {
    entity.setState("IN_PROGRESS");
    entity.setRetryCount(currentRetry); // currentRetry venía en 0 y sobreescribía la BD
    return repository.save(entity);
})
```

**✅ La Solución (Implementada):**
Se lee el valor real de la base de datos `entity.getRetryCount()`, se conserva al guardarlo, y se sincroniza de vuelta hacia el objeto de Dominio en memoria (`doc.setRetryCount`) para que el Caso de Uso original tenga el conteo real.

```java
// CÓDIGO CORREGIDO
.flatMap(entity -> {
    // 1. Leemos la verdad absoluta de la BD
    int realRetry = entity.getRetryCount() != null ? entity.getRetryCount() : 0;
    
    entity.setState("IN_PROGRESS");
    entity.setRetryCount(realRetry); // 2. Conservamos el valor de BD
    
    return documentRepository.save(entity)
            .doOnNext(saved -> {
                // 3. Sincronizamos el objeto de memoria
                doc.setId(saved.getId());
                doc.setRetryCount(realRetry); 
            });
})
```

---

### 3. Patrón Abstracto para Múltiples Bases de Datos (Multi-Schema)

**❌ El Problema (Antes):**
Se necesitaba homologar (buscar equivalencias) tanto en la base de datos "Master" (para SOAP) como en la "Animales" (para Animal). Intentar hacer un repositorio genérico fallaba porque Spring Data R2DBC aísla las conexiones por carpeta.

**✅ La Solución (Implementada):**
En lugar de repetir la lógica, se creó una clase abstracta donde centralizamos la regla de negocio, obligando a las clases hijas a suministrar el repositorio físicamente correcto.

```java
// 1. La clase abstracta define el "Qué hacer"
public abstract class AbstractHomologationAdapter {
    
    // Método abstracto: El hijo me entregará el repositorio correcto
    protected abstract R2dbcRepository<HomologationEntity, Long> getRepository();

    // La lógica de negocio no se repite
    public Mono<HomologationResult> resolve(DocumentHistoryDTO history) {
        return getRepository().findByCode(history.getCode())
            .map(entity -> new HomologationResult(entity.getFolder(), entity.getCountry()));
    }
}

// 2. El hijo específico en el paquete MasterDB
@Component
public class SoapHomologationAdapter extends AbstractHomologationAdapter {
    private final MasterDbHomologationRepository repo;
    
    @Override
    protected R2dbcRepository<HomologationEntity, Long> getRepository() {
        return this.repo; // Inyecta conexión a Master
    }
}

// 3. El hijo específico en el paquete AnimalDB
@Component
public class AnimalHomologationAdapter extends AbstractHomologationAdapter {
    private final AnimalDbHomologationRepository repo;
    
    @Override
    protected R2dbcRepository<HomologationEntity, Long> getRepository() {
        return this.repo; // Inyecta conexión a Animales
    }
}
```

---

### 4. Blindaje contra `NullPointerException` en Reactor

**❌ El Problema (Antes):**
Si se buscaba un documento para actualizarlo y no existía, el método devolvía `Mono.empty()`. Reactor interrumpe la cadena de operadores silenciosamente ante un `empty`, por lo que el código que debía insertar un registro nuevo simplemente se "saltaba".

**✅ La Solución (Implementada):**
Utilizar `.switchIfEmpty()` para garantizar que, si el registro no existe en base de datos (por ser un documento nuevo), el código tenga un plan de respaldo para insertar el nuevo registro de forma segura.

```java
// CÓDIGO CORREGIDO
return documentRepository.findByStatesAndUseCaseToday(...)
    .filter(entity -> entity.getDocumentId().equals(doc.getDocumentId()))
    .next() // Garantiza extraer 1 solo elemento si existe
    .flatMap(entity -> {
        // ACTUALIZAR REGISTRO EXISTENTE
        entity.setState("IN_PROGRESS");
        return documentRepository.save(entity).thenReturn(1L);
    })
    .switchIfEmpty(Mono.defer(() -> {
        // INSERTAR REGISTRO NUEVO SI NO SE ENCONTRÓ
        AnimalDocumentEntity newEntity = AnimalDocumentEntity.builder()
                .documentId(doc.getDocumentId())
                .state("IN_PROGRESS")
                .retryCount(0)
                .build();
        return documentRepository.save(newEntity).thenReturn(1L);
    }));
```
