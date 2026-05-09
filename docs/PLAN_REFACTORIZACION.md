# Plan de Refactorización y Estabilidad Técnica

Este documento detalla las áreas de mejora identificadas en el servicio, enfocadas en resiliencia, optimización de memoria (OOM) y mejoras en las comunicaciones externas. Cada punto incluye ejemplos del código actual y la propuesta de solución.

---

## 1. Problemas Críticos (Estabilidad y Memoria)

### 1.1 `ProductHandler.java` - Pérdida de Contexto Reactive (Bug)
**Problema:** Al ejecutar el caso de uso de forma asíncrona con `.subscribe()`, se pierde el `Context` de WebFlux. Esto causa que `ProductRestGatewayAdapter` falle silenciosamente al intentar extraer el `traceId`. Además, el endpoint retorna un `200 OK` en lugar de un `202 ACCEPTED` para procesos asíncronos.

**Antes:**
```java
// ...
syncDocumentsUseCase.execute(useCase)
    .doOnError(error -> log.log(Level.SEVERE, "Error...", error))
    .subscribe(); // <- PIERDE EL CONTEXTO Y SE DESVINCULA

return ServerResponse.ok().bodyValue(...);
```

**Después:**
```java
// ...
syncDocumentsUseCase.execute(useCase)
    .doOnError(error -> log.error("Error...", error))
    .contextWrite(ctx) // <- MANTIENE EL TRACE ID
    .subscribe();

return ServerResponse.accepted().bodyValue(...); // <- HTTP 202
```

### 1.2 `ZipDecompressor.java` - Riesgo de OOM (Out of Memory)
**Problema:** El código actual lee **todo** el contenido de todos los archivos dentro del ZIP y los almacena en una `List` en memoria RAM al mismo tiempo. Si el ZIP pesa 100MB y contiene 50 archivos grandes, la memoria se disparará.

**Antes:**
```java
List<ProductDocumentHistory> entries = new ArrayList<>();
try (ZipInputStream zis = new ZipInputStream(...)) {
    while ((entry = zis.getNextEntry()) != null) {
        byte[] decompressed = zis.readAllBytes(); // CARGA EN MEMORIA
        entries.add(buildProductDocument(..., decompressed)); // ACUMULA EN LISTA
    }
}
return Flux.fromIterable(entries);
```

**Después (Uso de Streams/Flujos Lazy):**
```java
// Se delega a un Flux asíncrono usando Schedulers para no bloquear, 
// o se usa Flux.generate para extraer de a un archivo a la vez y liberarlo de memoria
// mediante Garbage Collection antes de pasar al siguiente.
return DataBufferUtils.readInputStream(...)
    .map(dataBuffer -> parseNextZipEntry(dataBuffer));
```

---

## 2. Mejoras en Gateways (Comunicaciones Externas)

### 2.1 `ProductRestGatewayAdapter.java` - Variables URI e Hilos Bloqueantes
**Problema:** Las variables de la URL se inyectan concatenando o reemplazando strings de forma manual (riesgo de mal encoding). Además, decodificar Base64 pesados en el hilo principal bloquea el Event Loop de Reactor.

**Antes:**
```java
String path = properties.productDocumentsPath().replace("{productId}", productId);
return webClient.get()
    .uri(path + "/{documentId}", documentId)
    // ...
    .map(response -> mapToProductDocument(productId, response)); // Base64 bloqueante
```

**Después:**
```java
return webClient.get()
    // Uso nativo de WebClient para variables seguras
    .uri(properties.productDocumentsPath() + "/{documentId}", productId, documentId)
    // ...
    // Se aísla el procesamiento pesado en un hilo de soporte elástico
    .publishOn(Schedulers.boundedElastic())
    .map(response -> mapToProductDocument(productId, response));
```

### 2.2 `SoapGatewayAdapter.java` - Manejo de Errores Limpio
**Problema:** Validación manual e ineficiente de tipos de errores en los `retryWhen`.

**Antes:**
```java
if (error instanceof WebClientResponseException) {
    int status = ((WebClientResponseException) error).getStatusCode().value();
    return status == 500 || status == 502 || status == 503;
}
return error instanceof TimeoutException;
```

**Después:**
```java
if (error instanceof WebClientResponseException ex) {
    return ex.getStatusCode().is5xxServerError();
}
return error instanceof TimeoutException || error instanceof WebClientRequestException;
```
