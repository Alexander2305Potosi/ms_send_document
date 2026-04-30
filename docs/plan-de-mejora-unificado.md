# Plan de Mejora Unificado - File Processor Service v3.0

> **Fecha:** 2026-04-30
> **Branch:** `feature/v3.0`
> **Autores del análisis:** Agente Senior Backend (20 años exp.) + Agente Junior Backend (3 años exp.)
> **Propósito:** Este documento consolida los hallazgos de ambos análisis independientes en un plan de acción único para validación manual y ejecución.

---

## 1. Resumen Ejecutivo Consolidado

El servicio `file-processor-service` es un microservicio Spring Boot 3.3.5 + WebFlux + R2DBC + Java 21 que implementa un pipeline de procesamiento de documentos con arquitectura hexagonal (carga de productos desde API REST externa, envío de documentos vía SOAP o S3). La arquitectura está bien planteada y el Template Method en `AbstractDocumentProcessingUseCase` es un punto de entrada claro.

**Fortalezas identificadas:** separación dominio/infraestructura nítida, mocks de servidor excelentes para desarrollo local, protección contra ZIP bombs, parser XML seguro, uso de records inmutables y mensajes de commit claros.

**Problemas críticos unificados (ambos agentes coinciden):**

| # | Problema | Impacto |
|---|----------|---------|
| 1 | `ProcessingException` (dominio) importa `ApiConstants` (infraestructura) — viola el Dependency Rule | Arquitectónico |
| 2 | `DatabaseInitializer` usa `.subscribe()` fire-and-forget — la app arranca aunque falle la BD | Disponibilidad |
| 3 | `ZipProcessor` ejecuta extracción ZIP bloqueante en event loop con `Mono.just()` | Rendimiento |
| 4 | Sin backpressure en pipeline de procesamiento — carga todos los documentos sin paginación | Escalabilidad |
| 5 | `ProductHandler.loadProducts()` devuelve body HTTP vacío — el cliente no recibe confirmación | Funcional |
| 6 | Cobertura de tests superficial (~25-30%) — use cases y repositorios sin tests reales | Calidad |
| 7 | Lógica duplicada de validación de archivos entre `FileValidator` y `ZipProcessor` | Mantenibilidad |
| 8 | Sin handler global de errores REST ni autenticación en endpoints | Seguridad |

**Deuda técnica total estimada:** 85-100 horas.

---

## 2. Hallazgos Consolidados por Categoría

### 2.1 Arquitectura y Diseño

| ID | Hallazgo | Ubicación | Perspectiva Senior | Perspectiva Junior | Severidad |
|----|----------|-----------|--------------------|--------------------|-----------|
| ARC-01 | Dominio depende de infraestructura (Dependency Rule) | `domain/exception/ProcessingException.java:3` | Violación más grave de la arquitectura hexagonal | Rompe la independencia del dominio | Crítica |
| ARC-02 | Paquetes inconsistentes `app-service` vs `app.service` | `application/app-service/config/` y `application/app/service/config/` | Viola convenciones Java | Confunde al desarrollar — ¿cuál es el correcto? | Media |
| ARC-03 | Circuit Breaker declarado pero sin implementar | `application.yml:80-85`, `build.gradle.kts:71-72` | Dependencia y config ociosas | Intención no ejecutada — genera expectativa falsa | Baja |
| ARC-04 | `ComponentScan` no incluye paquete `domain` | `Application.java:14-17` | Beans de dominio invisibles a escaneo automático | Si alguien pone `@Component` en dominio, no funciona | Media |
| ARC-05 | `DomainConfig` en capa incorrecta | `application/app/service/config/DomainConfig.java` | Debería estar en infraestructura o módulo de configuración | — | Baja |
| ARC-06 | Acoplamiento frágil entre perfiles SOAP/S3 | `ProductHandler.java:70-76` | `IllegalStateException` en runtime si perfil no coincide | — | Alta |
| ARC-07 | `ObjectProvider<S3DocumentProcessingUseCase>` con `getIfAvailable()` lanza excepción no recuperable | `ProductHandler.java:70-76` | — | Si un perfil no está activo, explota sin mensaje claro | Alta |

### 2.2 Calidad de Código y Legibilidad

| ID | Hallazgo | Ubicación | Perspectiva Senior | Perspectiva Junior | Severidad |
|----|----------|-----------|--------------------|--------------------|-----------|
| COD-01 | `SoapGatewayAdapter.sendSoap()`: ~80 líneas, 4 responsabilidades | `infrastructure/drivenadapters/soap/SoapGatewayAdapter.java:57-136` | Mono.deferContextual orquesta todo | "Un junior miraría esto y cerraría el IDE" | Alta |
| COD-02 | `retrieveDocument()`: 34 líneas, mezcla descarga + builder de 13 campos + persistencia | `domain/usecase/AbstractDocumentProcessingUseCase.java:48-81` | Si falla el builder, el contenido ya se persistió | Estado inconsistente posible | Alta |
| COD-03 | `ProductRestGatewayAdapter` usa `Map<String, Object>` con castings inseguros | `infrastructure/drivenadapters/restclient/ProductRestGatewayAdapter.java:85-136` | — | `ClassCastException` en runtime si la API cambia tipos | Alta |
| COD-04 | `R2dbcProductDocumentRepository.mapRowToDocument()` sin manejo de nulos explícito | `infrastructure/drivenadapters/r2dbc/R2dbcProductDocumentRepository.java:48-63` | — | Error de mapping de tipos da `IllegalArgumentException` sin contexto | Media |
| COD-05 | Uso inconsistente de `var` | `ProductStatusAggregator.java:37,76,101` | Inconsistencia con el resto del código | — | Baja |
| COD-06 | FQN en lugar de imports (7+ ocurrencias) | `ZipArchive.java:25,142`, `ZipProcessor.java:199`, `FileValidator.java:114`, `ProcessingException.java:3-4` | Violación de clean code | Dificulta lectura rápida | Baja |
| COD-07 | `toSoapXml()` recibe parámetro `documentId` que no usa | `infrastructure/helpers/soap/mapper/SoapMapper.java:39-57` | — | "Quien llame espera que aparezca en el XML" | Baja |
| COD-08 | `ApiConstants.HEADER_TRACE_ID = "message-id"` no intuitivo | `infrastructure/entrypoints/rest/constants/ApiConstants.java:22` | — | Se esperaría `X-Trace-Id` o `trace-id` | Baja |

### 2.3 Principios SOLID

| ID | Hallazgo | Ubicación | Perspectiva Senior | Severidad |
|----|----------|-----------|--------------------|-----------|
| SOL-01 | SRP: `FileValidator` con 4 responsabilidades (validar extensión, tamaño, extraer folder info, parsear extensiones) | `domain/usecase/FileValidator.java:45-114` | Rompe Single Responsibility | Alta |
| SOL-02 | OCP: `ProductHandler` con if-else hardcodeado para seleccionar procesador | `infrastructure/entrypoints/rest/handler/ProductHandler.java:69-83` | Agregar nuevo procesador requiere modificar la clase | Alta |
| SOL-03 | ISP: `FileValidationConfig` mezcla configuración con lógica de decisión | `domain/port/in/FileValidationConfig.java:22-28` | `shouldValidateSize()`/`shouldValidateExtension()` son lógica, no solo datos | Media |
| SOL-04 | LSP: Template Method con métodos `final` que impiden extensión | `domain/usecase/AbstractDocumentProcessingUseCase.java` | Subclases no pueden extender el flujo | Baja |

### 2.4 Código Duplicado

| ID | Hallazgo | Ubicación | Líneas duplicadas | Severidad |
|----|----------|-----------|--------------------|-----------|
| DUP-01 | FolderInfo extraction + validación + DocumentToUpload | `S3DocumentProcessingUseCase.java:60-65` y `SoapDocumentProcessingUseCase.java:46-51` | ~15 líneas | Alta |
| DUP-02 | Manejo de errores `onErrorResume` idéntico | `S3DocumentProcessingUseCase.java:91-100` y `SoapDocumentProcessingUseCase.java:76-85` | ~10 líneas | Alta |
| DUP-03 | Extracción de extensión de archivo en 3 lugares | `ProductDocumentInfo.extension()`, `FileValidator.java:100-104`, `ZipProcessor.java:186-189` | ~5 líneas × 3 | Media |
| DUP-04 | `parseAllowedExtensions()` duplicado | `FileValidator.java:106-114` y `ZipProcessor.java:194-203` | ~10 líneas | Media |
| DUP-05 | Métodos idénticos en 4 servidores mock (resolvePort, isPortAvailable, saveServerInfo, cleanupServerInfo, etc.) | `DocumentRestMock.java`, `PortableSoapMock.java`, `ProductRestMock.java`, `S3Mock.java` | ~200 líneas × 4 | Media |
| DUP-06 | Constantes DEFAULT_MAX_ENTRIES / DEFAULT_MAX_UNCOMPRESSED_SIZE duplicadas | `ZipArchive.java:39-40` y `ZipProcessor.java:191-192` | 2 líneas | Baja |

### 2.5 Manejo de Errores y Excepciones

| ID | Hallazgo | Ubicación | Perspectiva Senior | Perspectiva Junior | Severidad |
|----|----------|-----------|--------------------|--------------------|-----------|
| ERR-01 | `.subscribe()` sin bloqueo en inicialización de BD | `DatabaseInitializer.java:27-34` | La app arranca aunque falle la BD | "¿Por qué no usar spring.sql.init?" | Crítica |
| ERR-02 | `onErrorResume` en crash recovery asume siempre "tabla no existe" | `DatabaseInitializer.java:59-62` | Oculta errores reales de conexión | — | Alta |
| ERR-03 | Estrategia de error inconsistente: 5xx propaga, 4xx retorna fallido, timeout propaga | `SoapGatewayAdapter.java:95-136` | Sin criterio documentado | Sin política clara | Alta |
| ERR-04 | `ProcessingException.withTraceId()` llamado con string vacío | `SoapMapper.java:82` | Trazabilidad rota | — | Alta |
| ERR-05 | `withDocumentId()` sin null guard en traceId | `ProcessingException.java:51-53` | Inconsistencia con otros constructores | — | Media |
| ERR-06 | Sin `@ControllerAdvice` ni `ErrorWebExceptionHandler` global | No existe | Sin estandarización de respuestas de error REST | — | Alta |
| ERR-07 | Content=null ambiguo: significa "descargar bajo demanda" y "sin contenido" | `LoadProductsUseCase.java:86` | Sin diferenciación semántica | — | Media |
| ERR-08 | `S3GatewayAdapter.handleS3Error()` solo distingue TimeoutException vs resto | `infrastructure/drivenadapters/aws/S3GatewayAdapter.java:89-109` | — | Errores 403/503 caen en `UNKNOWN_ERROR` | Media |
| ERR-09 | Orden confuso en `DatabaseInitializer`: reset de PROCESSING antes de CREATE TABLE | `DatabaseInitializer.java:39-43` | — | Si la tabla no existe, el reset falla silenciosamente | Media |

### 2.6 Testing

| ID | Hallazgo | Ubicación | Severidad |
|----|----------|-----------|-----------|
| TST-01 | Sin test para `S3DocumentProcessingUseCase` | No existe archivo | Crítica |
| TST-02 | `SoapDocumentProcessingUseCaseTest` solo prueba `implementationName()` | `domain/usecase/SoapDocumentProcessingUseCaseTest.java:43-46` | Crítica |
| TST-03 | Sin tests para `R2dbcProductDocumentRepository` y `R2dbcProductRepository` | No existen archivos | Alta |
| TST-04 | Sin test para `S3GatewayAdapter` | No existe archivo | Alta |
| TST-05 | Sin test para `ProductRestGatewayAdapter` | No existe archivo | Alta |
| TST-06 | Sin test para `ZipProcessor` | No existe archivo | Alta |
| TST-07 | Sin test para `ProductHandler` | No existe archivo | Alta |
| TST-08 | Sin test para `ProductRoutes` | No existe archivo | Alta |
| TST-09 | `DocumentFlowIntegrationTest`: `assertThat(true).isTrue()` — no prueba nada | `e2e/DocumentFlowIntegrationTest.java:18-22` | Alta |
| TST-10 | Tests de entidad que solo validan builders de Lombok (valor probatorio cero) | `ProductInfoTest.java`, `ProductToProcessTest.java`, `FileUploadResultTest.java` | Media |
| TST-11 | `FileValidatorTest` sin escenarios negativos (nombres nulos, sin extensión, listas vacías) | `domain/usecase/FileValidatorTest.java` | Media |
| TST-12 | `ProductStatusAggregatorTest` sin prueba del método principal `updateProductStatus()` | `domain/usecase/ProductStatusAggregatorTest.java` | Media |
| TST-13 | `ZipArchiveTest` sin pruebas de ZIP bomb (>1000 entradas, >100MB) | `domain/entity/ZipArchiveTest.java` | Media |
| TST-14 | `FileValidatorTest` usa `@MockitoSettings(strictness = Strictness.LENIENT)` — oculta stubbings innecesarios | `domain/usecase/FileValidatorTest.java:22` | Baja |
| TST-15 | `SoapGatewayAdapterTest.createTestRequest()` vacío | `infrastructure/drivenadapters/soap/SoapGatewayAdapterTest.java:57-59` | Baja |

### 2.7 Rendimiento y Concurrencia

| ID | Hallazgo | Ubicación | Severidad |
|----|----------|-----------|-----------|
| PER-01 | `ZipInputStream` bloqueante en event loop con `Mono.just()` | `ZipProcessor.java:118-139` | Crítica |
| PER-02 | Sin backpressure — carga todos los documentos sin paginación | `AbstractDocumentProcessingUseCase.java:31-39` | Crítica |
| PER-03 | Sin configuración de pool R2DBC | `application.yml` | Alta |
| PER-04 | `CompletableFuture` en pipeline reactivo S3 con `Mono.fromFuture()` | `S3GatewayAdapter.java:61-63` | Alta |
| PER-05 | Sin configuración explícita de Schedulers | Todo el proyecto | Media |
| PER-06 | Riesgo de race condition en reinicialización de contexto Spring | `DatabaseInitializer.java:27-34` | Media |
| PER-07 | `Thread.sleep()` en `PortableSoapMock` | `PortableSoapMock.java:300-304` | Baja |
| PER-08 | Sin manejo de cancelación reactiva (`doOnCancel()`) | `AbstractDocumentProcessingUseCase` | Media |

### 2.8 Seguridad

| ID | Hallazgo | Ubicación | Severidad |
|----|----------|-----------|-----------|
| SEC-01 | Sin autenticación en endpoints REST | `ProductRoutes.java` — `/api/v1/products/load`, `/api/v1/products` | Crítica |
| SEC-02 | `S3Properties` permite credenciales vacías (sin `@NotBlank`) | `infrastructure/drivenadapters/aws/config/S3Properties.java:22-24` | Alta |
| SEC-03 | Sanitización de nombres incompleta (solo `..` y `/`) | `S3GatewayAdapter.java:126-131` | Alta |
| SEC-04 | `TransformerFactory` sin protecciones XXE | `SoapEnvelopeWrapper.java:70` | Alta |
| SEC-05 | Sin límite en decodificación Base64 (riesgo OOM) | `R2dbcProductDocumentRepository.java:69` | Media |
| SEC-06 | Sin validación de parámetro `processor` contra whitelist | `ProductHandler.java:55-57` | Media |

### 2.9 Observabilidad (Logging y Trazabilidad)

| ID | Hallazgo | Ubicación | Severidad |
|----|----------|-----------|-----------|
| OBS-01 | Logs en dominio sin traceId | `ZipProcessor.java:128`, `FileValidator.java:48` | Alta |
| OBS-02 | `R2dbcProductDocumentRepository.save()` sin `doOnError()` | `R2dbcProductDocumentRepository.java:138` | Alta |
| OBS-03 | `Mono.deferContextual` inconsistente (gateways sí, use cases no) | Todo el proyecto | Media |
| OBS-04 | Pipeline sin logs de etapa/elemento en errores | `AbstractDocumentProcessingUseCase.java:31-39` | Media |

### 2.10 Configuración y Build

| ID | Hallazgo | Ubicación | Severidad |
|----|----------|-----------|-----------|
| CFG-01 | `server.shutdown: immediate` en producción | `application-prod.yml:7` | Crítica |
| CFG-02 | Timeouts/retries inconsistentes entre entornos (dev=5s/1, prod=15s/2, default=30s/3) | `application-*.yml` | Alta |
| CFG-03 | Configuración SOAP duplicada entre perfil default y s3 | `application.yml:40-45` y `application.yml:93-104` | Media |
| CFG-04 | `application-windows.yml` mezcla `app.soap` y `app.processors.soap` | `application-windows.yml:3-11` | Media |
| CFG-05 | Perfil dev sin configuración S3 | `application-dev.yml` | Media |
| CFG-06 | PITest paths incorrectos | `build.gradle.kts:113-114` | Media |
| CFG-07 | Gradle daemon deshabilitado | `gradle.properties:5` | Baja |
| CFG-08 | JVM arg `-XX:+EnableDynamicAgentLoading` no estándar | `build.gradle.kts:98` | Baja |
| CFG-09 | `application-windows.yml` sin `app.document-rest` | `application-windows.yml` | Baja |

### 2.11 Código Muerto y Defectos

| ID | Hallazgo | Ubicación | Tipo |
|----|----------|-----------|------|
| DEAD-01 | Método `isValidUtf8()` nunca referenciado | `Base64Utils.java:39-47` | Código muerto |
| DEAD-02 | Constantes `FILENAME_TOO_LONG` e `INVALID_FILENAME` sin uso | `ProcessingResultCodes.java:21-22` | Código muerto |
| DEAD-03 | Método `findPendingProducts()` sin llamadas desde use cases | `R2dbcProductRepository.java:26-46` | Código muerto |
| DEAD-04 | Import `java.util.UUID` no usado | `R2dbcProductDocumentRepository.java:17` | Import muerto |
| DEAD-05 | Wrapper `UUID` innecesario en S3Mock | `S3Mock.java:281-285` | Código muerto |
| DEAD-06 | `createTestRequest()` vacío | `SoapGatewayAdapterTest.java:57-59` | Método vacío |
| BUG-01 | Body HTTP vacío en `loadProducts()` | `ProductHandler.java:43-51` | Bug funcional |
| BUG-02 | `claimDocument` solo PENDING pero `findPendingDocuments` incluye PROCESSING + RETRY | `R2dbcProductDocumentRepository.java:96-112` | Bug de estado |
| BUG-03 | Posible NPE en `content.length` sin null-check | `S3GatewayAdapter.java:53` | Bug potencial |
| BUG-04 | Strings vacíos `""` en lugar de NULL para campos ausentes | `R2dbcProductDocumentRepository.java:125-134` | Corrupción semántica |
| BUG-05 | `shouldValidateSize()`/`shouldValidateExtension()` flags no consultados por `FileValidator` | `FileValidationConfig.java:22-28` vs `FileValidator.java` | Bug o código muerto |
| BUG-06 | `DocumentRestMock` parece legacy — ¿sigue siendo necesario? | `mock/DocumentRestMock.java` | Deuda de limpieza |

---

## 3. Plan de Acción por Sprints

### Sprint 1: Estabilización Crítica (P0) — 13 horas

> **Objetivo:** Resolver los problemas que impiden un despliegue seguro en producción.

| ID | Tarea | Ubicación | Solución | Esfuerzo | Validación |
|----|-------|-----------|----------|----------|------------|
| P0-01 | Corregir violación del Dependency Rule | `ProcessingException.java:3` | Duplicar `HEADER_TRACE_ID` en dominio o crear `shared-kernel` con constantes compartidas | 4h | [x] `ProcessingException` no importa nada de `infrastructure` |
| P0-02 | BD bloqueante en startup | `DatabaseInitializer.java:27-34` | Reemplazar `.subscribe()` por `InitializingBean` con inicialización síncrona o implementar health check que bloquee hasta que BD esté lista | 3h | [x] Health endpoint refleja estado real de BD |
| P0-03 | ZIP bloqueante en event loop | `ZipProcessor.java:118-139` | Cambiar `Mono.just()` por `Mono.fromCallable()` + `subscribeOn(Schedulers.boundedElastic())` | 6h | [x] Extracción ZIP no bloquea hilos del event loop |

### Sprint 2: Cobertura de Tests y Manejo de Errores (P1) — 25 horas

> **Objetivo:** Establecer red de seguridad con tests reales y estandarizar el manejo de errores.

| ID | Tarea | Ubicación | Solución | Esfuerzo | Validación |
|----|-------|-----------|----------|----------|------------|
| P1-01 | Tests para `S3DocumentProcessingUseCase` | Crear `S3DocumentProcessingUseCaseTest.java` | Tests con mocks cubriendo folder exclusion, pipeline exitoso, pipeline con error, documentos sin contenido | 6h | [x] Cobertura >80% del use case |
| P1-02 | Tests reales para `SoapDocumentProcessingUseCase` | `SoapDocumentProcessingUseCaseTest.java` | Reemplazar test de `implementationName()` por tests del pipeline completo con gateways mockeados | 4h | [x] Cobertura >80% del use case |
| P1-03 | Tests para repositorios R2DBC | Crear `R2dbcProductDocumentRepositoryTest.java`, `R2dbcProductRepositoryTest.java` | `@DataR2dbcTest` con H2 en memoria, probar todas las operaciones CRUD y queries | 6h | [ ] Cobertura >80% de repositorios |
| P1-04 | `server.shutdown: immediate` → `graceful` | `application-prod.yml:7` | Cambiar valor a `graceful` con timeout adecuado | 1h | [x] Prod config usa graceful shutdown |
| P1-06 | Eliminar duplicación de validación de archivos | `FileValidator.java` + `ZipProcessor.java` | Extraer `FileValidationUtils` con métodos `extractExtension()`, `parseAllowedExtensions()`, `validateFile()` | 2h | [x] Lógica de validación en un solo lugar |
| P1-07 | Tests para `ProductHandler` | Crear `ProductHandlerTest.java` | `WebTestClient` verificando endpoints, selección de procesador, y casos de error | 5h | [ ] Endpoints probados con HTTP real |
| P1-08 | Documentar `calculateStatusFromCounts()` | `ProductStatusAggregator.java:117-141` | Agregar tabla de verdad en Javadoc documentando prioridad de evaluación de estados | 1h | [x] Javadoc explica cada combinación |

### Sprint 3: Deuda Técnica y Calidad (P2) — 27 horas

> **Objetivo:** Eliminar código duplicado, mejorar diseño y fortalecer seguridad.

| ID | Tarea | Ubicación | Solución | Esfuerzo | Validación |
|----|-------|-----------|----------|----------|------------|
| P2-01 | Estandarizar manejo de errores en `SoapGatewayAdapter` | `SoapGatewayAdapter.java:95-136` | Definir política documentada: excepciones para errores técnicos, `ExternalServiceResponse` para errores de negocio. Refactorizar `sendSoap()` extrayendo `buildRequest()`, `executeWithRetry()`, `mapResponse()` | 3h | [ ] Método <40 líneas, política documentada |
| P2-02 | Refactorizar `retrieveDocument()` | `AbstractDocumentProcessingUseCase.java:48-81` | Separar en: `downloadDocument()`, `updateDocumentContent()`, `buildDocumentToUpload()`. Garantizar atomicidad. | 3h | [ ] Sin estado inconsistente si falla a mitad |
| P2-03 | Extraer clase base `AbstractMockServer` | 4 archivos mock | Eliminar ~200 líneas duplicadas por mock. Heredar `resolvePort`, `isPortAvailable`, `saveServerInfo`, `cleanupServerInfo` | 3h | [ ] Cada mock <100 líneas |
| P2-04 | E2E test real | `DocumentFlowIntegrationTest.java` | Reemplazar `assertThat(true).isTrue()` por flujo completo con `WebTestClient` y mocks | 5h | [ ] Test verifica flujo carga → procesamiento → resultado |
| P2-05 | Sanitización robusta de nombres S3 | `S3GatewayAdapter.java:126-131` | Whitelist `[a-zA-Z0-9._-]` + protección contra null bytes y caracteres de control | 2h | [x] Path traversal bloqueado para todos los vectores |
| P2-06 | Protección XXE en `TransformerFactory` | `SoapEnvelopeWrapper.java:70` | Agregar `FEATURE_SECURE_PROCESSING`, deshabilitar DTD, entidades externas, igual que `DocumentBuilderFactory` | 1h | [x] Mismas protecciones que el parser seguro |
| P2-07 | `@NotBlank` en credenciales S3 | `S3Properties.java:22-24` | Agregar validación Bean Validation para accessKey/secretKey | 0.5h | [x] Arranque falla si credenciales vacías |
| P2-08 | Unificar nombres de paquetes | `application/app-service/` → `application/app/service/` | Renombrar directorio y paquete a `application.service.config` | 1h | [x] Sin guiones en paquetes Java |
| P2-09 | `doOnCancel()` en pipeline | `AbstractDocumentProcessingUseCase.java` | Revertir documentos a PENDING si el pipeline se cancela | 3h | [x] Cancelación no deja documentos en PROCESSING |
| P2-10 | Corregir `claimDocument` vs `findPendingDocuments` | `R2dbcProductDocumentRepository.java:96-112` | Revisar query/filtro para consistencia. Si RETRY y PROCESSING no son "pending", no incluirlos en la consulta | 2h | [x] Sin documentos huérfanos en RETRY |

### Sprint 4: Pulido y Optimización (P3) — 14 horas

> **Objetivo:** Limpiar código muerto, estandarizar estilo y afinar configuración.

| ID | Tarea | Ubicación | Solución | Esfuerzo | Validación |
|----|-------|-----------|----------|----------|------------|
| P3-01 | Unificar nombres de métodos en puertos | `SoapGateway.java`, `S3Gateway.java` | Renombrar `sendSoap`/`upload` a `send` o `transfer` en ambos puertos | 1h | [ ] Puertos usan mismo verbo |
| P3-02 | Eliminar `isValidUtf8()` | `Base64Utils.java:39-47` | Borrar método | 0.5h | [x] Sin referencias rotas |
| P3-03 | Eliminar constantes sin uso en `ProcessingResultCodes` | `ProcessingResultCodes.java:21-22` | Borrar `FILENAME_TOO_LONG` e `INVALID_FILENAME` | 0.5h | [x] Sin referencias rotas |
| P3-04 | Eliminar `findPendingProducts()` o implementar su uso | `R2dbcProductRepository.java:26-46` | Decidir: borrar o conectar a caso de uso | 0.5h | [x] Sin métodos huérfanos |
| P3-05 | Eliminar wrapper `UUID` en S3Mock | `S3Mock.java:281-285` | Usar `java.util.UUID` directamente | 0.5h | [x] Sin clase interna redundante |
| P3-06 | Eliminar `DocumentRestMock` si es legacy | `mock/DocumentRestMock.java` | Verificar si se usa. Si no, borrar | 0.5h | [x] Sin mocks legacy |
| P3-07 | Corregir PITest target classes | `build.gradle.kts:113-114` | Usar paths reales: `infrastructure.drivenadapters.*` | 1h | [ ] PITest ejecuta sobre paquetes correctos |
| P3-08 | Reemplazar magic strings por enums | `ProductStatusAggregator.java:103-109` | Usar `DocumentStatus.SUCCESS.name()` en lugar de "SUCCESS" | 0.5h | [x] Sin strings literales para estados |
| P3-09 | Reemplazar FQN por imports | 7+ archivos | Agregar imports y eliminar FQN | 1h | [ ] Sin FQN en código |
| P3-10 | Activar Gradle daemon | `gradle.properties:5` | Cambiar a `true` | 0.5h | [x] Build time mejora |
| P3-11 | Eliminar `createTestRequest()` vacío | `SoapGatewayAdapterTest.java:57-59` | Borrar método | 0.5h | [x] Sin métodos vacíos |
| P3-12 | Mover valores mágicos a properties | `S3GatewayAdapter.java`, `SoapGatewayAdapter.java`, `ZipArchive.java` | Externalizar: `S3_KEY_PREFIX`, `DEFAULT_TIMEOUT`, `MAX_ERROR_BODY_LENGTH`, límites ZIP | 2h | [ ] Valores configurables sin recompilar |
| P3-13 | Revisar JVM arg no estándar | `build.gradle.kts:98` | Verificar necesidad de `-XX:+EnableDynamicAgentLoading` | 0.5h | [ ] Sin flags JVM sospechosos |
| P3-14 | Eliminar `@MockitoSettings(LENIENT)` | `FileValidatorTest.java:22` | Quitar stubbings no usados del setUp en lugar de ocultarlos | 0.5h | [ ] Strict mode sin warnings |
| P3-15 | Reemplazar `Map<String,Object>` por DTO tipado | `ProductRestGatewayAdapter.java:85-136` | Crear records para respuestas JSON y usar Jackson ObjectMapper | 4h | [ ] Sin castings inseguros |

### Sprint 5: Observabilidad (P2) — 8 horas

> **Objetivo:** Trazabilidad completa end-to-end y logging adecuado para debugging en producción.

| ID | Tarea | Ubicación | Solución | Esfuerzo | Validación |
|----|-------|-----------|----------|----------|------------|
| OBS-01 | traceId en MDC para herencia automática | Hooks globales | `Hooks.onEachOperator()` con `MDC.put("traceId")` en todo el pipeline reactivo | 3h | [ ] traceId aparece en logs sin pasarlo manualmente |
| OBS-02 | Agregar `doOnError()` con logs | `R2dbcProductDocumentRepository.java` | `.doOnError(e -> log.error("Save failed: {}", e.getMessage(), e))` en save, update, delete | 1h | [ ] Fallos de BD generan log |
| OBS-03 | Agregar traceId a logs de dominio | `ZipProcessor.java:128`, `FileValidator.java:48` | Incluir traceId en mensajes de log | 1h | [ ] Logs de dominio trazables |
| OBS-04 | Enriquecer logs de error en pipeline | `AbstractDocumentProcessingUseCase.java:31-39` | Incluir documentId y etapa del pipeline en `doOnError` | 2h | [ ] Mensaje de error identifica documento y etapa |
| OBS-05 | Diferenciar errores S3 en `handleS3Error()` | `S3GatewayAdapter.java:89-109` | Distinguir 403 (permisos), 503 (throttling), 404 (no encontrado) y mapear a códigos específicos | 1h | [ ] Cada tipo de error S3 tiene código distinto |

---

## 4. Checklist de Validación Manual

### 4.1 Validación Pre-Implementación

| # | Criterio | Estado |
|---|----------|--------|
| V-01 | ¿Se entienden y aceptan todas las prioridades asignadas (P0-P3)? | [ ] |
| V-02 | ¿Hay hallazgos que deban descartarse por no aplicar al contexto real? | [ ] |
| V-03 | ¿El esfuerzo estimado es realista para el equipo? | [ ] |
| V-04 | ¿Hay dependencias entre tareas que requieran reordenamiento? | [ ] |
| V-05 | ¿Se requiere aprobación de arquitectura para cambios estructurales (ARC-01, ARC-02)? | [ ] |
| V-06 | ¿Hay restricciones de negocio que impidan ciertos cambios (ej: nombre del header "message-id")? | [ ] |

### 4.2 Validación por Sprint

#### Sprint 1 — Estabilización Crítica

| ID | Criterio de aceptación | Estado |
|----|------------------------|--------|
| P0-01 | `ProcessingException` y todas las clases en `domain/` no importan nada de `infrastructure/` | [x] |
| P0-02 | Health check de BD refleja conectividad real. La app no arranca si la BD no está disponible | [x] |
| P0-03 | `ZipProcessor` usa `boundedElastic`. Thread dump muestra extracción fuera del event loop | [x] |

#### Sprint 2 — Tests y Errores

| ID | Criterio de aceptación | Estado |
|----|------------------------|--------|
| P1-01 | `S3DocumentProcessingUseCaseTest` con >80% de cobertura y aserciones de comportamiento | [x] |
| P1-02 | `SoapDocumentProcessingUseCaseTest` con >80% de cobertura y aserciones de comportamiento | [x] |
| P1-03 | Tests de repositorios cubren todas las operaciones. Se ejecutan en CI | [ ] |
| P1-04 | `application-prod.yml` usa `graceful`. Test de integración verifica shutdown ordenado | [x] |
| P1-06 | `FileValidationUtils` es el único punto de validación. `FileValidator` y `ZipProcessor` lo usan | [x] |
| P1-07 | `ProductHandlerTest` cubre ambos endpoints y casos de error con `WebTestClient` | [ ] |
| P1-08 | Javadoc de `calculateStatusFromCounts()` documenta tabla de verdad con prioridades | [x] |

#### Sprint 3 — Calidad y Seguridad

| ID | Criterio de aceptación | Estado |
|----|------------------------|--------|
| P2-01 | `send()` <40 líneas. Política de errores en Javadoc de la clase | [x] |
| P2-02 | Si falla `buildDocumentToUpload()`, el contenido no queda persistido sin trazabilidad | [x] |
| P2-03 | `AbstractMockServer` como clase base. Mocks individuales <100 líneas cada uno | [x] |
| P2-04 | E2E test real verifica flujo completo | [ ] |
| P2-05 | Test unitario con `../../etc/passwd%00.jpg` devuelve nombre sanitizado seguro | [x] |
| P2-06 | `TransformerFactory` tiene `FEATURE_SECURE_PROCESSING` y entidades externas deshabilitadas | [x] |
| P2-07 | Test: `S3Properties` sin credenciales lanza excepción en startup | [x] |
| P2-08 | `find . -name "*.java" | xargs grep "package.*app.service"` sin guiones. Sin `app-service/` en paths | [x] |
| P2-09 | Al cancelar suscripción del pipeline, `findPendingDocuments()` retorna documentos revertidos | [x] |
| P2-10 | `claimDocument` y `findPendingDocuments` operan sobre el mismo conjunto lógico | [x] |

#### Sprint 4 — Pulido

| ID | Criterio de aceptación | Estado |
|----|------------------------|--------|
| P3-01 | `SoapGateway.send()` y `S3Gateway.send()` — mismo nombre de método | [x] |
| P3-02 | `isValidUtf8` eliminado. `grep -r "isValidUtf8" src/` no devuelve resultados | [x] |
| P3-03 | Constantes eliminadas sin referencias rotas | [x] |
| P3-04 | `findPendingProducts()` está en uso O eliminado. Sin métodos huérfanos | [x] |
| P3-05 | Sin clase `UUID` interna en `S3Mock` | [x] |
| P3-06 | `DocumentRestMock` eliminado O justificado y documentado | [x] |
| P3-07 | `./gradlew pitest` ejecuta mutaciones sobre paquetes reales | [x] |
| P3-08 | Cero strings literales de estado en `ProductStatusAggregator` | [x] |
| P3-09 | `grep -r "java\.util\.\|java\.time\.\|java\.stream\.Collectors" src/main/` sin resultados (FQN) | [x] |
| P3-10 | `./gradlew build` sin flag `--no-daemon` explícito. Tiempo de build reducido | [x] |
| P3-11 | `SoapGatewayAdapterTest` sin métodos vacíos | [x] |
| P3-12 | Constantes movidas a `@ConfigurationProperties`. Test verifica que se leen de YAML | [x] |
| P3-13 | Sin flag `EnableDynamicAgentLoading` en `build.gradle.kts` | [x] |
| P3-14 | `FileValidatorTest` sin `@MockitoSettings(strictness = Strictness.LENIENT)` | [x] |
| P3-15 | `ProductRestGatewayAdapter` sin `Map<String, Object>` ni castings. Usa records tipados | [x] |

#### Sprint 5 — Observabilidad

| ID | Criterio de aceptación | Estado |
|----|------------------------|--------|
| OBS-01 | `traceId` aparece automáticamente en logs de todas las capas sin pasarlo como parámetro | [ ] |
| OBS-02 | Fallo de INSERT genera log con stacktrace y contexto | [x] |
| OBS-03 | Todos los logs del dominio incluyen `traceId` | [ ] |
| OBS-04 | Mensaje de error en pipeline: "Pipeline error at stage=[RETRIEVE|UPLOAD|VALIDATE] for doc=[id]" | [x] |
| OBS-05 | Error S3 mapea 403→PERMISSION_DENIED, 503→THROTTLED, 404→NOT_FOUND en logs y respuesta | [x] |

### 4.3 Validación Post-Implementación

| # | Criterio | Estado |
|---|----------|--------|
| V-07 | Todos los tests pasan (`./gradlew test`) | [ ] |
| V-08 | Cobertura global >70% (`./gradlew jacocoTestReport`) | [ ] |
| V-09 | Sin regresiones en flujos E2E | [ ] |
| V-10 | Métricas de rendimiento igual o mejor que antes | [ ] |
| V-11 | Health check pasa en todos los entornos (dev, prod, windows) | [ ] |
| V-12 | Pipeline de CI/CD sin errores | [ ] |

---

## 5. Preguntas Abiertas para Resolver Antes de Implementar

Estas preguntas (aportadas por la perspectiva junior) requieren respuesta del equipo o stakeholders antes de ejecutar ciertas tareas:

| # | Pregunta | Bloquea | Responsable | Respuesta |
|---|----------|---------|-------------|-----------|
| Q-01 | ¿`DocumentRestMock` sigue siendo necesario o es legacy? | P3-06 | Tech Lead | legacy |
| Q-02 | ¿Por qué el header de traceId se llama `"message-id"`? ¿Hay integración con mensajería que obligue ese nombre? | P3-01 parcial | Arquitecto | si para trazabilidad |
| Q-03 | ¿Los métodos `shouldValidateSize`/`shouldValidateExtension` son para funcionalidad futura o es código muerto? | P2-03 parcial | Product Owner | codigo muerto |
| Q-04 | ¿Cuál es el nivel de concurrencia esperado? (informa decisiones de pool R2DBC y Schedulers) | P0-03 | Tech Lead | el endpoint solo se ejecuta una vez, y serian pocos productos con pocos documentos |
| Q-05 | ¿Hay plan de migrar de H2 a BD real? La config de prod no sobreescribe la URL de R2DBC | P0-02 | Arquitecto / DevOps | no hay plan |
| Q-06 | ¿Los mocks standalone en `src/test` no deberían estar en `src/main` o módulo separado? | P2-03 | Tech Lead | no |
| Q-07 | ¿Por qué `DatabaseInitializer` usa `.subscribe()` fire-and-forget en lugar de `spring.sql.init`? | P0-02 | Desarrollador original | aplica la mejor practica |

---

## 6. Métricas Finales

| Indicador | Valor Actual | Valor Objetivo |
|-----------|-------------|----------------|
| Cobertura de tests | ~25-30% | >70% |
| Código duplicado | ~800 líneas | <200 líneas |
| Violaciones de Dependency Rule | 1 confirmada | 0 |
| Operaciones bloqueantes en event loop | 1 (`ZipProcessor`) | 0 |
| Endpoints sin autenticación | 2 | 0 |
| Tiempo de build (Gradle) | Con daemon=false | Con daemon=true (~40% más rápido) |
| Total de hallazgos | 76 (senior) + 35 únicos (junior) | Resueltos: ~40 tareas (sin P0-04, P1-04, P1-08, P2-04, P2-05) |

---

## 7. Instrucciones de Uso

1. **Revisar** las preguntas abiertas (sección 5) con el equipo y stakeholders.
2. **Validar** prioridades y esfuerzos estimados. Reasignar si es necesario.
3. **Marcar** cada tarea como `[x]` tras implementación y revisión de código.
4. **Actualizar** este documento si surgen nuevos hallazgos durante la ejecución.
5. **No cerrar** un sprint sin que todas sus tareas tengan el checklist de validación marcado.

---

> **Fuentes:**
> - `docs/analysis-senior-backend.md` — 76 hallazgos, priorización P0-P3, recomendaciones estratégicas
> - `docs/analysis-junior-backend.md` — fricciones diarias, quick wins, bugs potenciales, preguntas de onboarding
