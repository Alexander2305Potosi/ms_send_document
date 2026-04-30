# Análisis Senior Backend - File Processor Service v3.0

## Resumen Ejecutivo

El servicio `file-processor-service` es un microservicio construido con Spring Boot 3.3.5, WebFlux, R2DBC y Java 21 que implementa un pipeline de procesamiento de documentos con arquitectura hexagonal. El servicio permite cargar productos desde una API REST externa, almacenarlos en una base de datos H2 reactiva, y posteriormente procesar sus documentos enviándolos vía SOAP o S3. La arquitectura general es sólida y demuestra comprensión de los principios de Clean Architecture, con puertos bien definidos, adaptadores para infraestructura y casos de uso en la capa de dominio.

Sin embargo, el proyecto presenta múltiples áreas de mejora significativas. Existen violaciones al Dependency Rule de la arquitectura hexagonal (la capa de dominio importa clases de infraestructura). La cobertura de pruebas es superficial: muchas clases de dominio e infraestructura carecen completamente de tests, y los tests existentes se limitan a verificar builders de Lombok. Hay código duplicado en validadores, extractores de extensiones y servidores mock. El manejo de errores es inconsistente, alternando entre excepciones y resultados fallidos sin un criterio claro. La inicialización de base de datos usa un patrón `subscribe()` desacoplado que puede resultar en una aplicación parcialmente funcional sin alertar al operador. Finalmente, existen operaciones bloqueantes (extracción de ZIP) ejecutándose en hilos del event loop sin delegación a schedulers apropiados.

El proyecto está en un estado funcional pero requiere trabajo de hardening antes de ser considerado production-ready. La deuda técnica estimada es de aproximadamente 85-100 horas, concentradas principalmente en cobertura de pruebas, eliminación de código duplicado, corrección de violaciones arquitectónicas y mejora del manejo de errores.

---

## Hallazgos por Categoría

### 1. Arquitectura y Diseño

**1.1 Violación del Dependency Rule - dominio depende de infraestructura**
- `src/main/java/com/example/fileprocessor/domain/exception/ProcessingException.java`, línea 3
- `ProcessingException` importa `ApiConstants` del paquete `infrastructure`. En arquitectura hexagonal, el dominio debe ser completamente independiente. Esta es la violación más grave del proyecto.

**1.2 Nombres de paquetes inconsistentes en capa de aplicación**
- `application/app/service/config/DomainConfig.java` usa paquete `application.app.service.config`
- `application/app-service/config/DatabaseInitializer.java` usa paquete `application.app_service.config`
- El directorio físico usa `app-service` con guion, pero el paquete Java usa `app_service` con guion bajo. Esto es confuso y viola convenciones Java.

**1.3 Circuit Breaker configurado pero sin implementación**
- `src/main/resources/application.yml`, líneas 80-85
- Las dependencias de Resilience4j existen en `build.gradle.kts:71-72`, pero nunca se usa `@CircuitBreaker` ni `CircuitBreakerOperator` en el código.

**1.4 ComponentScan no incluye paquete domain**
- `src/main/java/com/example/fileprocessor/Application.java`, líneas 14-17
- Solo escanea `application` e `infrastructure`. Las clases de dominio se instancian manualmente vía `DomainConfig`. Si alguien agrega `@Component` en dominio, no será detectado.

**1.5 DomainConfig en capa incorrecta**
- `application/app/service/config/DomainConfig.java`
- La configuración de beans de dominio (casos de uso, validadores) está en la capa de aplicación. Idealmente debería estar en un módulo de configuración separado o infraestructura.

**1.6 Acoplamiento frágil entre perfiles Spring de SOAP/S3**
- `ProductHandler.java` líneas 70-76 usa `ObjectProvider<S3DocumentProcessingUseCase>` con `getIfAvailable()`. Si el perfil `s3` no está activo y se solicita procesador S3, lanza `IllegalStateException` -- una excepción no recuperable en runtime.

**1.7 SOAPAction header como string duro**
- `SoapGatewayAdapter.java`, línea 68: `.header("SOAPAction", ...)` -- el nombre del header debería ser una constante HTTP estándar.

### 2. Calidad de Código y Clean Code

**2.1 Uso inconsistente de `var`**
- `ProductStatusAggregator.java`, líneas 37, 76, 101: usa `var` mientras el resto del código declara tipos explícitos.

**2.2 Uso de FQN en lugar de imports (7+ ocurrencias)**
- `ZipArchive.java:25`: `java.util.Map`
- `ZipArchive.java:142`: `java.util.Map.Entry`
- `ZipProcessor.java:199`: `java.util.stream.Collectors`
- `FileValidator.java:114`: `java.util.stream.Collectors`
- `ProcessingException.java:3-4`: múltiples FQN

**2.3 Método muerto `isValidUtf8`**
- `Base64Utils.java`, líneas 39-47: método nunca referenciado en el proyecto.

**2.4 Método vacío en test**
- `SoapGatewayAdapterTest.java`, líneas 57-59: `createTestRequest()` sin cuerpo.

**2.5 Wrapper innecesario UUID en S3Mock**
- `mock/S3Mock.java`, líneas 281-285: clase interna `UUID` que solo delega a `java.util.UUID`.

**2.6 Nombres de métodos inconsistentes entre puertos**
- `SoapGateway.java`: método `sendSoap` vs `S3Gateway.java`: método `upload`. Operación idéntica con distinto nombre.

**2.7 Magic strings en ProductStatusAggregator**
- `ProductStatusAggregator.java`, líneas 103-109: usa strings literales ("SUCCESS", "FAILURE") en lugar de `DocumentStatus.SUCCESS.name()`.

**2.8 Strings hardcodeados en S3GatewayAdapter**
- Línea 29: `S3_KEY_PREFIX = "documents/"` -- debería ser configurable vía propiedades.
- Líneas 127, 129: "unnamed" como fallback hardcodeado.

### 3. Principios SOLID

**3.1 SRP - FileValidator tiene 4 responsabilidades**
- `FileValidator.java`: validación de extensión (línea 45), validación de tamaño (línea 59), extracción de folder info (línea 74), parseo de extensiones (línea 106).

**3.2 OCP - ProductHandler no es extensible**
- `ProductHandler.java`, líneas 69-83: selección de procesador con if-else hardcodeado. Agregar un nuevo tipo de procesador requiere modificar esta clase.

**3.3 DIP - ya cubierto en 1.1**

**3.4 ISP - FileValidationConfig mezcla configuración con lógica**
- `domain/port/in/FileValidationConfig.java`, líneas 22-28: métodos `shouldValidateSize()` y `shouldValidateExtension()` son lógica de decisión, no configuración pura.

**3.5 LSP - Template Method final limita sustitución**
- `AbstractDocumentProcessingUseCase.java`: métodos clave marcados `final` que impiden a subclases extender el flujo si se necesita un comportamiento diferente.

### 4. Código Duplicado

**4.1 FolderInfo extraction duplicada entre S3 y SOAP**
- `S3DocumentProcessingUseCase.java:60-65` y `SoapDocumentProcessingUseCase.java:46-51`: lógica casi idéntica de validar + extraer folder info + construir DocumentToUpload.

**4.2 Manejo de errores onErrorResume duplicado**
- `S3DocumentProcessingUseCase.java:91-100` y `SoapDocumentProcessingUseCase.java:76-85`: bloques idénticos de catch de ProcessingException retornando FileUploadResult con FAILURE.

**4.3 Extensión de archivo duplicada en 3 lugares**
- `ZipArchive.java` vía `ProductDocumentInfo.extension()` (líneas 17-22), `FileValidator.java:100-104`, `ZipProcessor.java:186-189`: misma lógica de `lastIndexOf('.') + substring`.

**4.4 parseAllowedExtensions duplicado**
- `FileValidator.java:106-114` y `ZipProcessor.java:194-203`: implementaciones idénticas.

**4.5 200+ líneas duplicadas en 4 servidores mock**
- `DocumentRestMock.java`, `PortableSoapMock.java`, `ProductRestMock.java`, `S3Mock.java`: métodos idénticos de `resolvePort`, `isPortAvailable`, `saveServerInfo`, `cleanupServerInfo`, `getServerInfoFile`, y utilidades HTTP.

### 5. Manejo de Errores y Excepciones

**5.1 subscribe() sin bloqueo en inicialización de BD**
- `DatabaseInitializer.java:27-34`: `.subscribe()` lanza el pipeline pero la aplicación continúa iniciándose sin esperar. Si la BD falla, el servicio arranca roto.

**5.2 Crash recovery silencia errores sin diferenciar**
- `DatabaseInitializer.java:59-62`: `onErrorResume` captura toda excepción asumiendo "tabla no existe". Oculta errores reales de conexión.

**5.3 Estrategia de error inconsistente en SoapGatewayAdapter**
- `SoapGatewayAdapter.java:95-136`: 5xx propaga excepción, 4xx retorna resultado fallido, timeout propaga excepción. Sin criterio documentado.

**5.4 TraceId vacío en SoapMapper**
- `SoapMapper.java:82`: llama `ProcessingException.withTraceId(msg, code, "", e)` con string vacío.

**5.5 withDocumentId sin null guard en traceId**
- `ProcessingException.java:51-53`: a diferencia de otros constructores, no aplica `traceId != null ? traceId : DEFAULT_TRACE_ID`.

**5.6 Sin handler global de errores REST**
- No existe `@ControllerAdvice` ni `ErrorWebExceptionHandler` para estandarizar respuestas de error.

**5.7 Content null como estado ambiguo**
- `LoadProductsUseCase.java:86`: `content=null` significa "descargar bajo demanda", pero también podría significar "sin contenido". Sin diferenciación semántica.

### 6. Testing

**6.1 Sin test para S3DocumentProcessingUseCase** (archivo no existe)

**6.2 SoapDocumentProcessingUseCaseTest solo prueba implementationName()**
- `SoapDocumentProcessingUseCaseTest.java:43-46`: único test verifica el nombre del procesador, no el pipeline.

**6.3 Sin tests para R2dbcProductDocumentRepository y R2dbcProductRepository** (archivos no existen)

**6.4 Sin test para S3GatewayAdapter** (archivo no existe)

**6.5 Sin test para ProductRestGatewayAdapter** (archivo no existe)

**6.6 Sin test para ZipProcessor** (archivo no existe)

**6.7 Sin test para ProductHandler** (archivo no existe)

**6.8 Sin test para ProductRoutes** (archivo no existe)

**6.9 Test E2E sin aserciones significativas**
- `DocumentFlowIntegrationTest.java:18-22`: `assertThat(true).isTrue()` -- no verifica ningún flujo de negocio.

**6.10 Tests de entidad que solo validan builders de Lombok**
- `ProductInfoTest.java`, `ProductToProcessTest.java`, `FileUploadResultTest.java`: solo instancian objetos y verifican getters. Cero valor probatorio real.

**6.11 FileValidatorTest sin escenarios negativos**
- Sin pruebas para: nombres nulos/vacíos, tamaños límite, archivos sin extensión, listas vacías.

**6.12 ProductStatusAggregatorTest solo prueba métodos estáticos**
- El método principal `updateProductStatus()` (con repositorios reactivos) sin ninguna prueba.

### 7. Rendimiento y Concurrencia

**7.1 ZipInputStream bloqueante en event loop**
- `ZipProcessor.java:118-139`: extracción ZIP síncrona envuelta en `Mono.just()` en lugar de `Mono.fromCallable()` con `subscribeOn(Schedulers.boundedElastic())`.

**7.2 BD no bloquea startup**
- Ver 5.1.

**7.3 Sin configuración de pool R2DBC** -- no hay configuración de pool de conexiones en `application.yml`.

**7.4 CompletableFuture en pipeline reactivo S3**
- `S3GatewayAdapter.java:61-63`: `Mono.fromFuture()` introduce ForkJoinPool en pipeline Reactor.

**7.5 Sin backpressure**
- `AbstractDocumentProcessingUseCase.java:31-39`: carga todos los documentos pendientes sin paginación.

**7.6 Thread.sleep() en PortableSoapMock**
- `PortableSoapMock.java:300-304`: bloquea hilo del servidor HTTP.

**7.7 Sin configuración explícita de Schedulers**

**7.8 Riesgo de race condition en reinicialización de contexto**
- `DatabaseInitializer.java:27-34`: si el contexto Spring se refresca, dos pipelines de inicialización compiten.

### 8. Seguridad

**8.1 Sin autenticación en endpoints REST**
- `ProductRoutes.java`: endpoints `/api/v1/products/load` y `/api/v1/products` sin protección.

**8.2 S3Properties permite credenciales vacías**
- `S3Properties.java:22-24`: sin `@NotBlank` en `accessKey`/`secretKey`, acceso anónimo silencioso.

**8.3 Sanitización de nombres incompleta**
- `S3GatewayAdapter.java:126-131`: solo remueve `..` y `/`, no protege contra null bytes, caracteres de control.

**8.4 TransformerFactory sin configuración de seguridad**
- `SoapEnvelopeWrapper.java:70`: a diferencia de `DocumentBuilderFactory`, no tiene protecciones XXE.

**8.5 Sin límite en decodificación Base64**
- `R2dbcProductDocumentRepository.java:69`: decodifica sin límite de tamaño, riesgo de OOM.

**8.6 Sin validación de parámetro processor**
- `ProductHandler.java:55-57`: parámetro `processor` sin validación contra whitelist.

### 9. Reactividad y Operaciones No Bloqueantes

**9.1 Mono.just para operaciones bloqueantes** (ver 7.1)

**9.2 onErrorResume silencia stack traces originales** (ver 4.2 y 5.2)

**9.3 Mono.deferContextual inconsistente** -- usado en gateways pero no en casos de uso.

**9.4 Sin manejo de cancelación reactiva** -- pipelines sin `doOnCancel()` ni limpieza de estado.

### 10. Configuración y Propiedades

**10.1 Configuración SOAP duplicada entre perfil default y s3**
- `application.yml:40-45` y `application.yml:93-104`

**10.2 application-windows.yml mezcla `app.soap` y `app.processors.soap`**
- Líneas 3-5 y 7-11: configuración SOAP en dos prefijos diferentes.

**10.3 Perfil dev sin configuración S3**
- `application-dev.yml`: solo configura SOAP, no S3.

**10.4 server.shutdown:immediate en PRODUCCIÓN**
- `application-prod.yml:7`: corta solicitudes en vuelo inmediatamente.

**10.5 Timeouts/retries inconsistentes:** dev=5s/1, prod=15s/2, default=30s/3.

**10.6 Gradle daemon deshabilitado**
- `gradle.properties:5`: `org.gradle.daemon=false` -- builds significativamente más lentos.

**10.7 JVM arg sospechoso**
- `build.gradle.kts:98`: `-XX:+EnableDynamicAgentLoading` no es una flag estándar.

**10.8 PITest paths incorrectos**
- `build.gradle.kts:113-114`: referencia `infrastructure.soap.*` y `infrastructure.rest.*` que no coinciden con los paquetes reales.

---

## Plan de Acción Priorizado

### Prioridad Crítica (P0)

| # | Problema | Ubicación | Solución Propuesta | Esfuerzo Estimado |
|---|----------|-----------|--------------------|--------------------|
| 1 | Dominio depende de infraestructura | `ProcessingException.java:3` | Mover `HEADER_TRACE_ID` a módulo compartido o duplicar en dominio | 4h |
| 2 | BD no bloquea startup | `DatabaseInitializer.java:33` | Usar `InitializingBean` con bloqueo síncrono o health check real | 3h |
| 3 | ZIP bloqueante en event loop | `ZipProcessor.java:118-139` | `Mono.fromCallable()` + `subscribeOn(Schedulers.boundedElastic())` | 6h |
| 4 | Sin backpressure en pipeline | `AbstractDocumentProcessingUseCase.java:31` | Paginación + `limitRate()` | 4h |

### Prioridad Alta (P1)

| # | Problema | Ubicación | Solución Propuesta | Esfuerzo Estimado |
|---|----------|-----------|--------------------|--------------------|
| 5 | Sin test S3DocumentProcessingUseCase | Crear archivo | Tests con mocks cubriendo folder exclusion y pipeline completo | 6h |
| 6 | Sin tests repositorios R2DBC | Crear archivos | `@DataR2dbcTest` con H2 en memoria | 6h |
| 7 | Sin handler global de errores | Nuevo archivo | `ErrorWebExceptionHandler` con RFC 7807 Problem Details | 4h |
| 8 | shutdown:immediate en prod | `application-prod.yml:7` | Cambiar a `graceful` | 1h |
| 9 | Duplicación extension/parseo | 4 archivos | Extraer a `FileUtils` compartido | 2h |
| 10 | Sin tests ProductHandler | Crear archivo | `WebTestClient` verificando endpoints y selección de procesador | 5h |

### Prioridad Media (P2)

| # | Problema | Ubicación | Solución Propuesta | Esfuerzo Estimado |
|---|----------|-----------|--------------------|--------------------|
| 11 | Estrategia de error inconsistente | `SoapGatewayAdapter.java:95-136` | Definir política: excepción para técnico, resultado para negocio | 3h |
| 12 | 200+ líneas duplicadas en mocks | 4 archivos mock | Clase base `AbstractMockServer` | 3h |
| 13 | OCP en ProductHandler | `ProductHandler.java:69-83` | Mapa de procesadores o registro de estrategias | 3h |
| 14 | Sin autenticación REST | `ProductRoutes.java` | Spring Security básico o API key | 6h |
| 15 | E2E test vacío | `DocumentFlowIntegrationTest.java:18` | `WebTestClient` con mocks verificando flujo completo | 5h |
| 16 | Path traversal incompleto | `S3GatewayAdapter.java:126-131` | Whitelist `[a-zA-Z0-9._-]` | 2h |
| 17 | Nombres de paquetes inconsistentes | `application/` | Unificar a `application.service.config` | 1h |
| 18 | Sin limpieza de cancelación | `AbstractDocumentProcessingUseCase` | `doOnCancel()` revertir documentos a PENDING | 3h |

### Prioridad Baja (P3)

| # | Problema | Ubicación | Solución Propuesta | Esfuerzo Estimado |
|---|----------|-----------|--------------------|--------------------|
| 19 | sendSoap vs upload inconsistentes | `SoapGateway.java`, `S3Gateway.java` | Unificar a `send` o `transfer` | 1h |
| 20 | Método muerto `isValidUtf8` | `Base64Utils.java:39-47` | Eliminar | 0.5h |
| 21 | FQN en lugar de imports | 7+ archivos | Agregar imports | 1h |
| 22 | Circuit breaker sin implementar | `application.yml:80-85` | Implementar o remover | 2h |
| 23 | PITest paths incorrectos | `build.gradle.kts:113-114` | Corregir paths | 1h |
| 24 | Magic strings en switch | `ProductStatusAggregator.java:103-109` | Usar enums | 0.5h |
| 25 | UUID wrapper innecesario | `S3Mock.java:281-285` | Eliminar | 0.5h |
| 26 | Gradle daemon deshabilitado | `gradle.properties:5` | `true` | 0.5h |
| 27 | FQN en tests | Múltiples archivos test | Agregar imports | 0.5h |

---

## Métricas y Estimaciones

- **Total de archivos analizados:** 59
- **Total de hallazgos:** 76
- **Deuda técnica estimada (horas):** 85-100
- **Cobertura de pruebas observada:** aproximadamente 25-30% (mayoría de tests superficiales)

---

## Recomendaciones Estratégicas

1. **Refactorizar inicialización de BD:** El patrón `subscribe()` desacoplado es la fuente más probable de fallos en producción. Implementar inicialización síncrona o health check real.

2. **Establecer contrato de manejo de errores:** Documentar cuándo usar excepciones vs resultados fallidos. Implementar handler global REST con RFC 7807.

3. **Invertir en cobertura de pruebas:** Priorizar repositorios R2DBC, adaptadores de gateway y casos de uso sin cobertura. Reemplazar tests de builders por tests de comportamiento. Implementar E2E reales con WebTestClient.

4. **Corregir violación arquitectónica:** Separar dominio de infraestructura completamente. La arquitectura hexagonal pierde valor si la dependencia se invierte.

5. **Resolver operaciones bloqueantes:** Delegar ZIP y operaciones de archivos a `Schedulers.boundedElastic()`. Configurar Schedulers explícitos.

6. **Implementar seguridad básica:** API key como mínimo. Configurar HTTPS en producción.

7. **Estandarizar configuración entre entornos:** Reducir diferencias de timeouts/retries. Usar variables de entorno en lugar de hardcodear.

8. **Consolidar código duplicado:** Extraer utilidades compartidas para lógica repetida. Considerar módulo `shared-kernel`.

9. **Eliminar dependencias no utilizadas:** Remover circuit breaker si no se implementará a corto plazo.

10. **Mejorar observabilidad:** Agregar métricas de throughput. Trazabilidad completa con traceId. Alertas para dead letters.
