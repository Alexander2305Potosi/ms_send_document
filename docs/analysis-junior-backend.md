# Análisis Junior Backend - File Processor Service v3.0

## Resumen Ejecutivo

Este es un microservicio Spring Boot WebFlux que procesa documentos asociados a productos, descargándolos desde una API REST externa y enviándolos a servicios SOAP o buckets S3. La arquitectura hexagonal está bien planteada en teoría: los use cases del dominio están limpios, las excepciones están bien modeladas y los adaptadores de infraestructura encapsulan detalles externos razonablemente. Como desarrollador junior que tendría que mantener este código día a día, me sentiría cómodo con la estructura general porque los patrones son consistentes y el Template Method en AbstractDocumentProcessingUseCase es un buen punto de entrada para entender el flujo.

Sin embargo, hay fricciones reales que alguien con 3 años de experiencia notaría de inmediato. El método `ProductHandler.loadProducts()` devuelve una respuesta HTTP vacía al cliente (`.thenMany(Mono.empty())`), lo que significa que si alguien invoca ese endpoint, no recibe confirmación de lo que pasó. La cobertura de tests es muy desigual: tienes el `SoapGatewayAdapterTest` que es excelente con MockWebServer y múltiples escenarios, pero `SoapDocumentProcessingUseCaseTest` solo prueba el nombre de la implementación y `DocumentFlowIntegrationTest` solo verifica que el contexto de Spring carga. Hay código que cualquier junior tocaría sin saber que rompe, como el `claimDocument` que solo reclama documentos en PENDING pero `findPendingDocuments` devuelve también PROCESSING y RETRY.

A nivel de productividad diaria, el proyecto tiene buena pinta pero necesita más cariño en los tests de dominio, consistencia en los paquetes y una revisión de los paths de configuración. Me gustaría ver más del trazado reactivo en los logs para poder seguir una petición de punta a punta sin abrir el debugger.

## Lo Que Me Gustó

1. **Arquitectura hexagonal bien definida.** La separación entre dominio (use cases, puertos) e infraestructura (adaptadores, entrypoints) es nítida. Si mañana cambiamos de R2DBC a MongoDB, solo tocaríamos los adaptadores.

2. **Template Method en AbstractDocumentProcessingUseCase.** El patrón está aplicado de forma correcta: `executePendingDocuments()` es final y define el esqueleto, mientras que `applyRulesMetadata()` y `uploadDocument()` son abstractos para que las subclases implementen su lógica específica.

3. **Mocks de servidores para desarrollo local.** PortableSoapMock, S3Mock, ProductRestMock y DocumentRestMock son un tesoro para hacer desarrollo sin depender de servicios externos. El PortableSoapMock tiene 6 escenarios que rotan infinitamente, lo que permite probar reintentos sin esfuerzo.

4. **Manejo de errores con reintentos.** `SoapGatewayAdapter` implementa retry con backoff usando `Retry.backoff()` de Reactor, filtrando excepciones retryable con una lógica clara.

5. **Protección contra ZIP bombs.** `ZipArchive` tiene límites de entradas (1000) y tamaño descomprimido (100MB), con chequeos durante la extracción entrada por entrada.

6. **Parser XML seguro.** `SoapEnvelopeWrapper` configura el `DocumentBuilderFactory` para deshabilitar DOCTYPE y entidades externas, previniendo ataques XXE.

7. **Uso de records para DTOs.** `ProductDocumentInfo`, `ProductStatusSummary`, `DocumentToUpload` y varios properties son records inmutables, lo que evita boilerplate y reduce bugs por mutabilidad.

8. **Mensajes de commit claros.** El historial de git muestra commits atómicos con mensajes descriptivos ("refactor: simplify ProductHandler and eliminate code duplication").

9. **Circuit breaker con Resilience4j.** La dependencia está declarada en el build, mostrando intención de proteger el servicio contra fallos en cascada.

10. **Configuración multi-perfil.** application.yml, dev, prod y windows separados, con variables de entorno para los endpoints.

## Puntos de Fricción Diarios

### 1. Legibilidad y Mantenibilidad

**A. `SoapGatewayAdapter.sendSoap()` es demasiado largo (~80 líneas).**
Archivo: `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/soap/SoapGatewayAdapter.java`, líneas 57-136
Este método hace todo dentro de un `Mono.deferContextual`: construye el SOAP envelope, hace la llamada HTTP, configura reintentos, parsea la respuesta, mapea errores HTTP, timeouts, errores de conexión e IO. Es el tipo de método que un junior miraría y cerraría el IDE. Sugeriría extraer al menos tres responsabilidades: construir la petición, ejecutar con reintentos, y mapear la respuesta/errores.

**B. `retrieveDocument()` en AbstractDocumentProcessingUseCase es denso (~34 líneas).**
Archivo: `src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java`, líneas 48-81
Este método descarga el documento, reconstruye el objeto `ProductDocumentToProcess` con un builder de 13 campos, persiste el contenido en base de datos y retorna un `DocumentToUpload`. Si falla en medio de la reconstrucción del builder, el contenido quedó persistido y puede ser un estado inconsistente. Separaría la descarga de la persistencia en métodos distintos.

**C. `ProductRestGatewayAdapter` usa `Map<String, Object>` para parsear JSON.**
Archivo: `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/restclient/ProductRestGatewayAdapter.java`, líneas 85-99, 101-136
El método `mapToProductDocumentInfo` hace castings inseguros: `(String) json.get("documentId")`, `(String) json.get("filename")`. Si la API externa cambia el tipo de un campo (por ejemplo, de String a Number), esto explota en runtime con ClassCastException sin ningún mensaje amigable. Preferiría usar Jackson ObjectMapper con un DTO o al menos validar los tipos antes de castear.

**D. `R2dbcProductDocumentRepository.mapRowToDocument()` no maneja nulos de forma explícita.**
Archivo: `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/R2dbcProductDocumentRepository.java`, líneas 48-63
Si una columna es NULL en la base de datos, `row.get("content", String.class)` devuelve null, y `decodeContent(null)` devuelve null, lo cual puede estar bien. Pero si hay un error de mapping de tipos (por ejemplo, la columna `created_at` no es TIMESTAMP), la excepción será un `IllegalArgumentException` genérico sin contexto de qué fila falló. Agregaría try-catch con más contexto.

**E. `ProductHandler.loadProducts()` retorna un body vacío.**
Archivo: `src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/handler/ProductHandler.java`, líneas 43-51
La línea `.thenMany(Mono.empty())` hace que el body sea un Flux vacío, por lo que el cliente recibe 202 Accepted pero sin contenido. Esto es confuso porque el método `bodyValue()` espera un Publisher, pero al hacer `.thenMany(Mono.empty())` estás anulando el resultado. Deberías retornar `ServerResponse.accepted().build()` o devolver el Flux de resultados real.

### 2. Nombrado y Convenciones

**A. Paquetes inconsistentes: `application.app-service.config` vs `application.app.service.config`.**
Archivos:
- `src/main/java/com/example/fileprocessor/application/app-service/config/DatabaseInitializer.java`
- `src/main/java/com/example/fileprocessor/application/app/service/config/DomainConfig.java`
El guion en `app-service` vs el punto en `app.service` es inconsistente. En Java, la convención es usar puntos como separadores de paquetes.

**B. `toSoapXml()` en SoapMapper recibe `documentId` pero no lo usa.**
Archivo: `src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/mapper/SoapMapper.java`, líneas 39-57
El parámetro `String documentId` se pasa al método pero nunca se utiliza dentro. Esto hace que quien llame al método espere que el documentId aparezca en alguna parte del XML generado, pero no es así.

**C. `ApiConstants.HEADER_TRACE_ID = "message-id"` no es intuitivo.**
Archivo: `src/main/java/com/example/fileprocessor/infrastructure/entrypoints/rest/constants/ApiConstants.java`, línea 22
La constante se llama `HEADER_TRACE_ID` pero su valor es `"message-id"`. Un desarrollador nuevo esperaría que el header HTTP fuera `X-Trace-Id` o `trace-id`.

**D. Nombres de archivos mock solapados: `DocumentRestMock` vs `ProductRestMock`.**
Ambos simulan una API REST de documentos, pero con endpoints diferentes. El código productivo solo usa ProductRestMock. DocumentRestMock parece ser un mock legacy que ya no se usa.

### 3. Complejidad y Curvas de Aprendizaje

**A. La pipeline reactiva es difícil de tracear.**
Archivo: `src/main/java/com/example/fileprocessor/domain/usecase/AbstractDocumentProcessingUseCase.java`, líneas 31-39
Cuando ocurre un error en producción, los logs te dicen "Pipeline error: ..." pero no te dicen en qué etapa exacta falló ni con qué documento, porque el mensaje genérico de `doOnError` pierde el contexto del elemento. Un junior intentando debugear esto pasaría horas agregando logs temporales.

**B. La lógica de `calculateStatusFromCounts()` en ProductStatusAggregator es compleja.**
Archivo: `src/main/java/com/example/fileprocessor/domain/usecase/ProductStatusAggregator.java`, líneas 117-141
Las combinaciones de estados son muchas y el orden de evaluación es crítico. Un junior que quiera agregar un nuevo estado fácilmente rompe la lógica si no entiende la prioridad. Documentaría la tabla de verdad en el Javadoc.

**C. `SoapEnvelopeWrapper` mezcla DOM, JAXB y Transformer.**
Archivo: `src/main/java/com/example/fileprocessor/infrastructure/helpers/soap/xml/SoapEnvelopeWrapper.java`, líneas 55-85
Para hacer unmarshalling de una respuesta SOAP, el código pasa por tres APIs XML diferentes. Si el formato SOAP cambia, arreglar esto requiere entender las tres.

**D. Duplicación de lógica de validación entre `FileValidator` y `ZipProcessor`.**
Archivos: `FileValidator.java` líneas 96-114 y `ZipProcessor.java` líneas 141-173
Ambos tienen métodos `parseAllowedExtensions()` y lógica de validación de extensión y tamaño. Si cambia la lógica de validación, hay que cambiarla en dos lugares.

### 4. Logging y Depuración

**A. Algunos logs carecen de traceId en el mensaje.**
- `ZipProcessor.java:128`: `log.info("ZIP {} contains {} documents", ...)` - no incluye traceId
- `FileValidator.java:48`: `log.warn("Document {} rejected...", ...)` - no incluye traceId
En una aplicación reactiva concurrente, los logs de distintos threads se mezclan. Sin traceId, es imposible saber qué log pertenece a qué petición.

**B. `R2dbcProductDocumentRepository` no loguea cuando una operación falla.**
Archivo: `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/r2dbc/R2dbcProductDocumentRepository.java`
`save()` usa `.doOnSuccess()` (línea 138) pero no tiene `.doOnError()`. Si falla un INSERT, el error se propaga silenciosamente hasta el use case.

**C. `S3GatewayAdapter.handleS3Error()` no diferencia todos los tipos de error.**
Archivo: `src/main/java/com/example/fileprocessor/infrastructure/drivenadapters/aws/S3GatewayAdapter.java`, líneas 89-109
Solo distingue TimeoutException vs "todo lo demás". Si S3 devuelve error de permisos (403) o throttling (503), todos caen en `UNKNOWN_ERROR`.

### 5. Tests y Cobertura

**A. `DocumentFlowIntegrationTest` solo verifica que el contexto carga.**
Archivo: `src/test/java/com/example/fileprocessor/e2e/DocumentFlowIntegrationTest.java`, líneas 18-22 - Este test siempre pasa (`assertThat(true).isTrue()`), no prueba ningún flujo real.

**B. `SoapDocumentProcessingUseCaseTest` solo prueba el nombre de implementación.**
Archivo: `src/test/java/com/example/fileprocessor/domain/usecase/SoapDocumentProcessingUseCaseTest.java`, líneas 44-46 - Es el test más débil del proyecto. Hay un use case entero de 87 líneas y el único test verifica que retorna "SOAP".

**C. `S3DocumentProcessingUseCase` NO tiene test dedicado.**
No existe un `S3DocumentProcessingUseCaseTest.java`. Si alguien rompe la lógica de exclusión de carpetas o el flujo de ZIP, no hay red de seguridad.

**D. `FileValidatorTest` usa `@MockitoSettings(strictness = Strictness.LENIENT)`.**
Archivo: `src/test/java/com/example/fileprocessor/domain/usecase/FileValidatorTest.java`, línea 22 - LENIENT oculta stubbings innecesarios. `shouldValidateSize()` y `shouldValidateExtension()` se configuran en setUp pero no se usan en ningún test.

**E. No hay tests para `ProductHandler`, `ProductRoutes`, `R2dbcProductDocumentRepository` o `R2dbcProductRepository`.** Componentes críticos sin cobertura.

**F. `ZipArchiveTest` no prueba escenarios de ZIP bomb.** No hay tests para: ZIP con más de 1000 entradas, ZIP con tamaño descomprimido > 100MB, etc.

### 6. Configuración y Build

**A. PIT mutation testing apunta a paquetes incorrectos.**
Archivo: `build.gradle.kts`, línea 113 - `targetClasses` referencia `com.example.fileprocessor.infrastructure.soap.*` pero el paquete real es `infrastructure.drivenadapters.soap.*`.

**B. Configuración duplicada de S3 en `application.yml`.**
Archivo: `src/main/resources/application.yml`, líneas 70-85 y 94-105 - La configuración de `app.aws.s3` aparece dos veces con valores diferentes (bloque principal y perfil s3).

**C. YAML inconsistente en `application-windows.yml`.** No define `app.document-rest`, por lo que si corres con perfil windows sin variable de entorno, usará el default del base.

### 7. Código Muerto e Imports No Usados

- `R2dbcProductDocumentRepository.java:17` - import `java.util.UUID` no usado
- `ProcessingResultCodes.java:21-22` - constantes `FILENAME_TOO_LONG` e `INVALID_FILENAME` nunca usadas
- `Base64Utils.java:39-47` - método `isValidUtf8()` nunca llamado desde código productivo
- `R2dbcProductRepository.java:26-46` - método `findPendingProducts()` implementado pero no llamado desde ningún use case
- `SoapMapper.java:39` - parámetro `documentId` en `toSoapXml()` nunca usado

### 8. Valores Mágicos y Hardcodeo

- `R2dbcProductDocumentRepository.java:125-134` - strings vacíos `""` para campos nulos, perdiendo la distinción entre "vacío" y "no proporcionado"
- `SoapGatewayAdapter.java:36` - `MAX_ERROR_BODY_LENGTH = 500` sin justificación ni configuración
- `S3GatewayAdapter.java:28-30` - `DEFAULT_TIMEOUT = 30s` y `S3_KEY_PREFIX = "documents/"` hardcodeados
- `ZipArchive.java:39-40` y `ZipProcessor.java:191-192` - duplican las mismas constantes DEFAULT_MAX_ENTRIES y DEFAULT_MAX_UNCOMPRESSED_SIZE

## Sugerencias Prácticas de Mejora

### Quick Wins (Menos de 1 hora)

| # | Mejora | Ubicación | Cómo Hacerlo |
|---|--------|-----------|---------------|
| 1 | Eliminar import no usado de java.util.UUID | R2dbcProductDocumentRepository.java:17 | Borrar la línea |
| 2 | Eliminar constantes no usadas FILENAME_TOO_LONG e INVALID_FILENAME | ProcessingResultCodes.java:21-22 | Borrar las líneas |
| 3 | Eliminar parámetro documentId no usado de toSoapXml() | SoapMapper.java:39 | Quitar String documentId de firma y llamada |
| 4 | Agregar doOnError con log en save() | R2dbcProductDocumentRepository.java:138 | Agregar `.doOnError(e -> log.error(...))` |
| 5 | Corregir PIT target classes | build.gradle.kts:113 | Usar los nombres reales de paquetes |
| 6 | Renombrar o documentar HEADER_TRACE_ID = "message-id" | ApiConstants.java:22 | Evaluar si debe ser X-Trace-Id |

### Mejoras de Medio Día

| # | Mejora | Ubicación | Cómo Hacerlo |
|---|--------|-----------|---------------|
| 7 | Arreglar ProductHandler.loadProducts() para retornar resultado real | ProductHandler.java:43-51 | Usar `ServerResponse.accepted().build()` o devolver Flux real |
| 8 | Extraer responsabilidades de SoapGatewayAdapter.sendSoap() en métodos privados | SoapGatewayAdapter.java:57-136 | Refactorizar en 3 métodos: buildRequest, executeWithRetry, mapResponse |
| 9 | Escribir test para S3DocumentProcessingUseCase | Crear S3DocumentProcessingUseCaseTest.java | Tests con mocks para folder exclusion y pipeline |
| 10 | Agregar tests reales para SoapDocumentProcessingUseCase | SoapDocumentProcessingUseCaseTest.java | Mockear gateways y verificar flujo completo |
| 11 | Refactorizar validación duplicada entre FileValidator y ZipProcessor | FileValidator.java + ZipProcessor.java | Extraer a clase compartida FileValidationUtils |
| 12 | Eliminar método findPendingProducts() no usado o implementar su uso | R2dbcProductRepository.java | Evaluar si es necesario o borrar |

### Mejoras de Día Completo o Más

| # | Mejora | Ubicación | Cómo Hacerlo |
|---|--------|-----------|---------------|
| 13 | Reemplazar Map<String, Object> en ProductRestGatewayAdapter por DTOs tipados | ProductRestGatewayAdapter.java | Usar Jackson ObjectMapper con records |
| 14 | Unificar la construcción de SOAP envelope con JAXB | SoapMapper.java | Usar marshalling JAXB en lugar de concatenación de strings |
| 15 | Agregar tests de integración reales usando los mocks existentes | Nuevo test | Usar los Mock servers para flujos end-to-end |
| 16 | Configurar traceId en MDC para trazabilidad automática | Hooks/reactor | Usar Hooks.onEachOperator con MDC |
| 17 | Hacer configurables los valores mágicos | application.yml | Mover timeouts, prefixes y límites a properties |
| 18 | Revisar nombres de paquetes para seguir convención de puntos | Estructura de directorios | Renombrar `app-service` a `app.service` |

## Errores y Defectos Encontrados

1. **Body HTTP vacío en loadProducts.** `ProductHandler.java:43-51` - `.thenMany(Mono.empty())` hace que el cliente reciba 202 Accepted sin contenido. Bug o intención mal comunicada.

2. **claimDocument solo reclama PENDING pero findPendingDocuments retorna PENDING + PROCESSING + RETRY.** `R2dbcProductDocumentRepository.java:96-112` - Documentos en RETRY nunca son "claimed". El `DatabaseInitializer` mitiga PROCESSING reseteándolos a PENDING, pero RETRY queda huérfano.

3. **Posible NPE en S3GatewayAdapter.** `S3GatewayAdapter.java:53` - `(long) content.length` sin null-check defensivo.

4. **R2dbcProductDocumentRepository guarda strings vacíos en lugar de NULL.** Líneas 125-134 - Corrompe la semántica de los datos para sistemas lectores externos.

5. **shouldValidateSize() y shouldValidateExtension() no se usan.** `FileValidationConfig.java:22-28` - FileValidator nunca consulta estos flags. O es código muerto o FileValidator tiene un bug por no respetarlos.

6. **Orden confuso en DatabaseInitializer.** `DatabaseInitializer.java:39-43` - reset de PROCESSING antes de CREATE TABLE. Si la tabla no existe, el reset falla silenciosamente.

7. **Duplicación de config S3 en application.yml.** Valores diferentes entre bloque principal y perfil s3 generan confusión sobre cuál prevalece.

## Preguntas Que Me Surgieron

1. ¿Por qué hay dos mocks de API REST (DocumentRestMock y ProductRestMock)? DocumentRestMock parece legacy. ¿Puedo borrarlo?
2. ¿Por qué el header de traceId se llama "message-id" y no "X-Trace-Id"? ¿Hay integración con mensajería que impone ese nombre?
3. Los métodos shouldValidateSize/Extension no se usan. ¿Son para funcionalidad futura?
4. ¿Cuál es el nivel de concurrencia esperado en la pipeline de executePendingDocuments?
5. ¿Por qué los documentos ZIP se guardan sin contenido (content=null) durante la carga inicial?
6. Los mocks standalone en src/test ¿no deberían estar en src/main o módulo separado?
7. ¿Hay plan de migrar de H2 en memoria a BD real? La config de prod no sobreescribe la URL de R2DBC.
8. ¿Por qué DatabaseInitializer usa ApplicationRunner con .subscribe() fire-and-forget en lugar de spring.sql.init?

## Conclusión Personal

Como junior con 3 años de experiencia, este proyecto me da una buena impresión general. La arquitectura hexagonal está respetada, el uso de Reactor es consistente, y las decisiones de diseño (Template Method, puertos y adaptadores, records inmutables) muestran que hay seniors detrás con criterio. Los mocks de servidor son un salvavidas para el desarrollo local.

Dicho esto, cuando me toque mantener esto en el día a día, las cosas que más me van a frenar son: (1) la falta de tests en los use cases de dominio, que hace que cualquier cambio sea un salto de fe, (2) la ausencia de traceId en los logs de dominio, que convierte el debugging en adivinanza, y (3) el método `ProductHandler.loadProducts()` que devuelve vacío, que es lo primero que un QA va a reportar como bug.

Prioritaria absolutamente la reparación del endpoint `/api/v1/products/load` (el body vacío) y la adición de tests para los dos use cases de procesamiento. Después de eso, la inclusión del traceId en el MDC para tracing end-to-end debería ser la siguiente batalla, porque sin eso, cuando algo falle en producción, vamos a estar mirando logs sin poder correlacionar eventos.

El proyecto está en buen camino. Solo necesita ese 20% extra de cariño en tests, logging y consistencia que hace la diferencia entre "funciona" y "es un placer mantenerlo".
