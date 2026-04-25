# File Processor Service

Microservicio reactivo basado en Spring WebFlux que obtiene documentos de una API REST externa y los envia a un servicio SOAP externo.

## Arquitectura (Clean Architecture Light)

El proyecto sigue **Clean Architecture simplificada** con 2 capas principales. La capa de **dominio es pura Java** (sin dependencias de frameworks), y la capa de **infraestructura** provee los beans Spring.

```
com.example.fileprocessor/
├── domain/                    # Capa de dominio (independiente de frameworks)
│   ├── entity/               # Entidades de negocio
│   │   ├── DocumentInfo.java        # Documento obtenido de REST
│   │   ├── DocumentToProcess.java   # Documento pendiente en BD
│   │   ├── FileData.java
│   │   ├── FileUploadResult.java
│   │   ├── SoapCommunicationLog.java
│   │   ├── SoapRequest.java
│   │   ├── SoapResponse.java
│   │   └── ZipArchive.java          # ZIP con documentos extraibles
│   ├── usecase/              # Casos de uso (logica de negocio)
│   │   ├── ProcessFileUseCase.java  # Obtiene de REST y envia a SOAP
│   │   └── FileValidator.java
│   ├── port/
│   │   ├── in/
│   │   │   └── FileValidationConfig.java
│   │   └── out/
│   │       ├── DocumentRestGateway.java  # Puerto para API REST documentos
│   │       └── ExternalSoapGateway.java
│   └── exception/            # Excepciones de dominio
│       ├── DomainException.java
│       └── FileValidationException.java
│
└── infrastructure/            # Capa de infraestructura (frameworks)
    ├── config/               # Configuracion (Properties, Beans)
    │   ├── DomainConfig.java          # Beans de casos de uso
    │   ├── FileUploadProperties.java
    │   └── WebFluxConfig.java
    ├── rest/                 # Adapter REST (entrada y Salida)
    │   ├── adapter/
    │   │   └── DocumentRestGatewayImpl.java  # Cliente REST para documentos
    │   ├── config/
    │   │   └── DocumentRestProperties.java
    │   ├── controller/
    │   │   └── FileController.java
    │   └── exception/
    │       └── GlobalErrorHandler.java
    └── soap/                 # Adapter SOAP (salida)
        ├── adapter/
        │   └── ExternalSoapGatewayImpl.java
        ├── config/
        │   └── SoapProperties.java
        ├── mapper/
        │   └── SoapMapper.java
        ├── xml/
        │   ├── SoapEnvelopeWrapper.java
        │   ├── SoapNamespaces.java
        │   └── model/
        │       ├── UploadFileRequest.java
        │       └── UploadFileResponse.java
        └── exception/
            └── SoapCommunicationException.java
```

### Reglas de Dependencia

- **Domain** no depende de ninguna otra capa (puro Java, sin frameworks, sin Spring)
- **Domain** no contiene anotaciones de framework (`@Component`, `@Service`, etc.)
- **Infrastructure** depende de Domain (acceso a casos de uso y entidades)
- **Infrastructure** expone los beans de dominio via `DomainConfig.java`

## JAXB - Serializacion SOAP

El proyecto utiliza **Jakarta XML Binding (JAXB)** para la serializacion/deserializacion de mensajes SOAP:

### Ventajas

- **Tipado fuerte**: Clases Java con anotaciones XML
- **Validacion automatica**: Esquemas XML validados en runtime
- **Mantenibilidad**: Cambios en el modelo son refactorizaciones seguras
- **Namespace handling**: Soporte completo de namespaces SOAP

### Generacion del XML

```java
// Marshalling (Java -> XML)
UploadFileRequest request = new UploadFileRequest(content, filename, ...);
String soapXml = soapMapper.toFullSoapMessage(request);

// Resultado:
// <?xml version="1.0" encoding="UTF-8"?>
// <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
//              xmlns:file="http://example.com/fileservice">
//   <soap:Header/>
//   <soap:Body>
//     <file:UploadFileRequest>
//       <file:content>dGVzdENvbnRlbnQ=</file:content>
//       <file:filename>document.pdf</file:filename>
//       ...
//     </file:UploadFileRequest>
//   </soap:Body>
// </soap:Envelope>
```

### Namespaces Centralizados

Los namespaces SOAP estan centralizados en `SoapNamespaces.java` para evitar hardcoding disperso:
- `SOAP_ENVELOPE`: `http://schemas.xmlsoap.org/soap/envelope/`
- `FILE_SERVICE`: `http://example.com/fileservice`

## Requisitos Previos

- **Java 21**
- **Docker** (opcional)
- **Gradle 8.5+**

## Compilacion

```bash
./gradlew clean build
```

## Ejecucion de Tests

```bash
# Tests unitarios e integracion
./gradlew test

# Reporte se genera en:
# build/reports/tests/test/index.html
```

## Coverage Reports (JaCoCo)

El proyecto incluye **JaCoCo** para generar reportes de cobertura de código:

```bash
# Generar reporte de cobertura
./gradlew jacocoTestReport

# Ver reporte HTML
open build/reports/jacoco/test/index.html

# El reporte XML se genera en:
# build/reports/jacoco/test/jacocoTestReport.xml
```

## SonarQube Analysis

Para ejecutar análisis estático con SonarQube:

```bash
# Opcion 1: Usar Docker Scanner
docker run --rm -v $(pwd):/usr/src sonarsource/sonar-scanner-cli

# Opcion 2: Descargar scanner manualmente
# https://docs.sonarsource.com/sonarqube/latest analysis/scan/sonarscanner/

# Configuracion requerida (variables de entorno o sonar-project.properties):
# SONAR_HOST_URL - URL del servidor SonarQube
# SONAR_TOKEN    - Token de autenticacion
```

El análisis cubre:
- Code smells
- Duplicated code
- Complexity metrics
- Coverage integration

## Mutation Testing (PIT)

El proyecto incluye **PIT (Programmed Instructional Testing)** para evaluar la calidad de las pruebas:

```bash
# Ejecutar mutation tests
./gradlew pitest

# Ver reporte HTML
open build/reports/pitest/index.html
```

### Configuracion

| Parametro | Valor |
|-----------|-------|
| Motor | PIT 1.15.0 |
| Plugin JUnit 5 | 1.2.1 |
| **Mutation Threshold** | **50%** |
| Coverage Threshold | 60% |
| Mutators | DEFAULTS + REMOVE_CONDITIONALS + INVERT_NEGS + MATH + NEGATE_CONDITIONALS + RETURN_VALS + VOID_METHOD_CALLS + NON_VOID_METHOD_CALLS |

Los mutators crean cambios artificiales en el codigo (ej: `>` -> `>=`, eliminar `if`). Si los tests detectan el cambio, el mutante "muere". El threshold del 50% garantiza que la mayoria de los cambios son detectados.

## Cobertura (JaCoCo)

| Parametro | Valor |
|-----------|-------|
| Version | JaCoCo 0.8.12 |
| Reporte XML | `build/reports/jacoco/test/jacocoTestReport.xml` |
| Reporte HTML | `build/reports/jacoco/test/index.html` |

## Ejecucion del Servicio

```bash
# Desarrollo (con perfil dev)
./gradlew bootRun --args='--spring.profiles.active=dev'

# Produccion
./gradlew bootRun --args='--spring.profiles.active=prod'
```

## Variables de Entorno

| Variable | Descripcion | Default |
|----------|-------------|---------|
| `SOAP_ENDPOINT` | URL del servicio SOAP | `http://localhost:9000/soap/fileservice` |
| `DOCUMENT_REST_ENDPOINT` | URL de la API REST de documentos | `http://localhost:3001` |

### Configuracion Automatica (Recomendada)

Al usar los scripts portables (`start-dev.bat` / `start-dev.sh`), la variable se configura automaticamente:

```bash
./start-dev.sh  # Detecta el puerto y configura SOAP_ENDPOINT
```

### Configuracion Manual

Si ejecutas manualmente, obten el endpoint del mock:

```bash
# Linux/Mac
export SOAP_ENDPOINT=$(cat /tmp/file-processor-mock.info | grep endpoint | cut -d= -f2)

# Windows
for /f "tokens=2 delims==" %a in ('type %TEMP%\file-processor-mock.info ^| findstr "endpoint"') do set SOAP_ENDPOINT=%a
```

Luego inicia el microservicio:

```bash
# Linux/macOS
./gradlew bootRun --args='--spring.profiles.active=dev'

# Windows
gradlew.bat bootRun --args='--spring.profiles.active=dev'
```

### Configuracion Fija (Sin Mock Variable)

Para usar siempre el mismo puerto (ej: 9000), define la variable manualmente:

**Linux/macOS:**
```bash
export SOAP_ENDPOINT=http://localhost:9000/soap/fileservice
./gradlew bootRun
```

**Windows:**
```cmd
set SOAP_ENDPOINT=http://localhost:9000/soap/fileservice
gradlew.bat bootRun
```

O configura permanentemente en Variables de Entorno del Sistema.

## Mock SOAP para desarrollo

El mock portable ahora soporta **6 escenarios de respuesta** rotando infinitamente:

| # | Escenario | HTTP Status | Delay | Reintentable |
|---|-----------|-------------|-------|--------------|
| 1 | **Success** | 200 | 100ms | - |
| 2 | **Server Error** | 500 | 100ms | Si |
| 3 | **Service Unavailable** | 503 | 100ms | Si |
| 4 | **Gateway Timeout** | 504 | 100ms | Si |
| 5 | **Slow Response** | 200 | **30s** | - |
| 6 | **Bad Request** | 400 | 100ms | No |

### Iniciar el Mock SOAP (Version Portable - Recomendada)

**Windows:**
```cmd
# Opcion 1: Script completo (Mock + Microservicio)
start-dev.bat

# Opcion 2: Solo el Mock
scripts\start-mock.bat

# Ver que puerto se uso:
type %TEMP%\file-processor-mock.info
```

**Linux/macOS:**
```bash
# Opcion 1: Script completo (Mock + Microservicio)
chmod +x start-dev.sh
./start-dev.sh

# Opcion 2: Solo el Mock
./scripts/start-mock.sh

# Ver que puerto se uso:
cat /tmp/file-processor-mock.info
```

### Ejecucion Manual con Puerto Especifico

```bash
# Compilar
./gradlew testClasses

# Iniciar con puerto especifico (todos los escenarios)
java -cp build/classes/java/test com.example.fileprocessor.mock.PortableSoapMock 9000

# Solo escenarios especificos (ej: solo 200 y 500)
java -cp build/classes/java/test com.example.fileprocessor.mock.PortableSoapMock 9000 1,2

# Configurar endpoint manualmente
export SOAP_ENDPOINT=http://localhost:9000/soap/fileservice
```

### Filtrar Escenarios

Puedes indicar qué escenarios responder separados por comas:

```bash
# Linux/Mac
./scripts/start-mock.sh 9000 1      # Solo exito
./scripts/start-mock.sh 9000 2,3,4    # Solo errores reintentables

# Windows
scripts\start-mock.bat 9000 1
scripts\start-mock.bat 9000 2,3,4
```

| # | Escenario |
|---|-----------|
| 1 | 200 Success |
| 2 | 500 Server Error |
| 3 | 503 Service Unavailable |
| 4 | 504 Gateway Timeout |
| 5 | 200 Slow Response (30s) |
| 6 | 400 Bad Request |

### Mock REST de Documentos

El mock de documentos (`DocumentRestMock`) simula una API REST externa que provee documentos:

| Endpoint | Descripcion |
|----------|-------------|
| `GET /api/documents` | Lista todos los documentos disponibles |
| `GET /api/document/{id}` | Obtiene un documento especifico por ID |

**Documentos disponibles:**

| ID | Nombre | Tipo Contenido | Origin |
|----|--------|----------------|--------|
| doc-001 | test-document.pdf | application/pdf | folderA/incoming |
| doc-002 | test-document.docx | application/vnd.openxmlformats-officedocument.wordprocessingml.document | folderB/incoming |
| doc-003 | test-document.txt | text/plain | folderA/special |

**Iniciar el Mock REST de Documentos:**

```bash
# Linux/macOS
chmod +x ./scripts/start-document-mock.sh
./scripts/start-document-mock.sh

# Ver que puerto se uso:
cat /tmp/document-rest-mock.info
```

**Windows:**
```cmd
scripts\start-document-mock.bat
type %TEMP%\document-rest-mock.info
```

### Mockoon Desktop

Si prefieres usar **Mockoon Desktop** en lugar del mock Java, puedes importar la configuracion:

```bash
# Importar en Mockoon Desktop
mockoon/document-rest-mock.json
```

**Puerto:** 3001 (configurable al abrir el archivo en Mockoon)

Endpoints configurados:
- `GET /api/documents` - Lista todos los documentos
- `POST /api/document/:id` - Documento por ID

### Mas informacion

- [`src/test/java/com/example/fileprocessor/mock/README.md`](src/test/java/com/example/fileprocessor/mock/README.md) - Documentacion completa del mock Java
- [`soapui/README.md`](soapui/README.md) - Alternativa usando SOAP UI (menos estable en Windows)

## API Endpoints

### POST /api/v1/files/load

Carga documentos desde la API REST externa y los guarda en la base de datos para su procesamiento posterior. Este es el primer paso del flujo de dos pasos.

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/files/load
```

**Response:**
```json
{
  "status": "LOADING",
  "message": "Document loading from REST API started",
  "correlationId": null,
  "traceId": "uuid-generado",
  "externalReference": null,
  "processedAt": "2024-01-15T10:30:00Z",
  "errorCode": null,
  "success": true
}
```

### GET /api/v1/files/{documentId}

Obtiene un documento de la API REST externa y lo envia al servicio SOAP.

**Request:**
```bash
curl -X GET http://localhost:8080/api/v1/files/doc-001
```

**Response:**
```json
{
  "status": "SUCCESS",
  "message": "File uploaded successfully",
  "correlationId": "corr-123-abc",
  "traceId": "uuid-generado",
  "processedAt": "2024-01-15T10:30:00Z",
  "externalReference": "ext-ref-456",
  "success": true
}
```

### GET /api/v1/files/{documentId} (ZIP)

Si el documento es un archivo ZIP, retorna un resultado consolidado:

```bash
curl -X GET http://localhost:8080/api/v1/files/doc-004
```

**Response:**
```json
{
  "status": "SUCCESS",
  "message": "ZIP processed: 3 documents, 3 successful",
  "correlationId": "corr-789-xyz",
  "traceId": "uuid-generado",
  "processedAt": "2024-01-15T10:30:01Z",
  "externalReference": "doc-004 (ZIP)",
  "success": true
}
```

### GET /api/v1/files

Obtiene todos los documentos disponibles de la API REST y los envia al servicio SOAP.

**Request:**
```bash
curl -X GET http://localhost:8080/api/v1/files
```

**Response:**
```json
[
  {
    "status": "SUCCESS",
    "message": "File uploaded successfully",
    "correlationId": "corr-123-abc",
    "traceId": "uuid-generado",
    "processedAt": "2024-01-15T10:30:00Z",
    "externalReference": "ext-ref-456",
    "success": true
  },
  {
    "status": "SUCCESS",
    "message": "File uploaded successfully",
    "correlationId": "corr-456-def",
    "traceId": "uuid-generado",
    "processedAt": "2024-01-15T10:30:01Z",
    "externalReference": "ext-ref-789",
    "success": true
  }
]
```

## Configuracion de Propiedades

El servicio utiliza `FileUploadProperties` para validar archivos:

| Propiedad | Descripcion | Default | Validacion |
|-----------|-------------|---------|------------|
| `app.file.max-size` | Tamano maximo en bytes | 10485760 (10MB) | Min 1024 |
| `app.file.allowed-types` | Tipos permitidos | pdf,docx,txt | No vacio |
| `app.file.max-filename-length` | Longitud maxima nombre | 255 | Min 10 |
| `app.file.max-file-size-mb` | Tamano maximo en MB | 50 | Min 1 |

## Restricciones de Archivos

- **Tamano maximo**: 10 MB
- **Tipos permitidos**: `pdf`, `docx`, `txt`
- **Nombre maximo**: 255 caracteres

### Validacion Temprana de Tamano

Antes de consumir el stream completo en memoria, el servicio verifica el `Content-Length` declarado por el cliente. Si excede el limite configurado, se rechaza inmediatamente con `FILE_SIZE_EXCEEDED`.

### Payload Too Large

Si el cuerpo de la peticion excede el buffer configurado en WebFlux (`maxInMemorySize`), el servicio responde con **HTTP 413 PAYLOAD_TOO_LARGE**.

## Procesamiento de Archivos ZIP

El servicio soporta el procesamiento de archivos comprimidos en formato ZIP. Cuando se recibe un documento ZIP:

1. **Deteccion Automatica**: El sistema detecta archivos con extension `.zip` o cuyo campo `isZip` sea `true`
2. **Extraccion**: El contenido del ZIP se extrae automáticamente
3. **Procesamiento Individual**: Cada archivo dentro del ZIP se procesa individualmente mediante el servicio SOAP
4. **Reporte Consolidado**: Se retorna un resultado consolidado con el total de documentos procesados

### Contenido del ZIP

El ZIP puede contener cualquier combinacion de:
- `*.pdf` - Documentos PDF
- `*.docx` - Documentos Word
- `*.txt` - Archivos de texto
- Otros formatos reconocidos

### Archivos Ignorados

El sistema ignora automaticamente:
- Directorios dentro del ZIP
- Archivos que comienzan con `_` (archivos de metadata)
- Archivos con nombre vacio

### Ejemplo de Respuesta para ZIP

```json
{
  "status": "SUCCESS",
  "message": "ZIP processed: 3 documents, 3 successful",
  "correlationId": "corr-123-abc",
  "traceId": "uuid-generado",
  "processedAt": "2024-01-15T10:30:00Z",
  "externalReference": "doc-004 (ZIP)",
  "success": true
}
```

## Reintentos SOAP

El servicio implementa **3 reintentos maximos** con backoff exponencial:

- **Escenarios reintentables**: Timeouts, errores 5xx (500, 502, 503, 504)
- **Delay**: 1s, 2s, 4s entre intentos
- **Logging**: Cada reintento es loggeado con traceId

### Estrategia de Timeout Dual

Para robustez ante conexiones lentas o "keep-alive tricks", se usan dos capas de timeout desfasadas 5 segundos:

| Capa | Timeout | Proposito |
|------|---------|-----------|
| Netty `responseTimeout` | `timeoutSeconds - 5` | Cancela la conexion si no hay respuesta de red |
| Reactor `.timeout()` | `timeoutSeconds` | Red de seguridad absoluta para el pipeline completo |

Esto evita que un servidor externo que envia bytes espaciados mantenga la conexion abierta indefinidamente.

```
INFO  - Sending SOAP request for traceId: abc-123, maxRetries=3
WARN  - Retrying SOAP call for traceId=abc-123, attempt 1/3 (backoff=1000ms)
WARN  - Retrying SOAP call for traceId=abc-123, attempt 2/3 (backoff=2000ms)
WARN  - Retrying SOAP call for traceId=abc-123, attempt 3/3 (backoff=4000ms)
ERROR - SOAP timeout for traceId: abc-123 after all retries exhausted
```

## Manejo de Errores

El `GlobalErrorHandler` centraliza el manejo de excepciones:

| Excepcion | HTTP Status | Codigo |
|-----------|-------------|--------|
| `FileValidationException` | 400 | `FILE_SIZE_EXCEEDED`, `INVALID_FILE_TYPE`, `FILENAME_TOO_LONG`, `INVALID_FILENAME` |
| `SoapCommunicationException` | 502/504/500 | `BAD_GATEWAY`, `GATEWAY_TIMEOUT`, `CLIENT_ERROR` |
| `DataBufferLimitException` | 413 | `PAYLOAD_TOO_LARGE` |
| `IllegalArgumentException` | 400 | `INVALID_ARGUMENT` |
| `ServerWebInputException` | 400 | `INVALID_REQUEST` |
| `RetryExhaustedException` | 504 | `GATEWAY_TIMEOUT` |

### Retry Exhausted

Cuando se agotan los reintentos configurados, el error es capturado y mapeado a `GATEWAY_TIMEOUT` con un mensaje descriptivo.

## Seguridad

### Proteccion XXE

El parser XML del `SoapEnvelopeWrapper` tiene configuradas protecciones contra ataques XXE (XML External Entity):

- `disallow-doctype-decl: true`
- `external-general-entities: false`
- `external-parameter-entities: false`

Esto previene que un atacante use DOCTYPE para leer archivos del servidor via respuestas SOAP maliciosas.

### Validacion de Nombres de Archivo

El `FileValidator` rechaza nombres que contengan:
- `..` (path traversal)
- `/` o `\` (separadores de directorio)
- Mas de 255 caracteres

## Testing con Postman

Ver carpeta `postman/`:

```bash
# Importar en Postman
postman/File-Processor-Service.postman_collection.json
```

Endpoints incluidos:
- GET /api/v1/files/{documentId} - Procesar documento especifico
- GET /api/v1/files - Procesar todos los documentos
- Health Check
- Error scenarios (400, 504)

Para mas detalles: [`postman/README.md`](postman/README.md)

## Scripts Disponibles

### Scripts Principales (Nuevos - Portable)

| Script | Plataforma | Descripcion |
|--------|------------|-------------|
| `./start-dev.sh` | Linux/Mac | **Inicia Mock + Microservicio** (auto-configura puerto) |
| `start-dev.bat` | Windows | **Inicia Mock + Microservicio** (auto-configura puerto) |
| `./scripts/start-mock.sh` | Linux/Mac | Mock SOAP portable (auto-detecta Java y puerto libre) |
| `scripts\start-mock.bat` | Windows | Mock SOAP portable (auto-detecta Java y puerto libre) |
| `./scripts/start-document-mock.sh` | Linux/Mac | Mock REST documentos (puerto 8081) |
| `scripts\start-document-mock.bat` | Windows | Mock REST documentos (puerto 8081) |
| `./scripts/stop-mock.sh` | Linux/Mac | Detener cualquier mock activo |
| `scripts\stop-mock.bat` | Windows | Detener cualquier mock activo |

## Testing con SOAP UI

Ver carpeta `soapui/`:

```bash
# Importar en SOAP UI
soapui/FileService-Mock-soapui-project.xml
```

Respuestas mock configuradas:
- ✅ Success (200)
- ❌ Server Error 500 (reintentable)
- ❌ Service Unavailable 503 (reintentable)
- ⏱️ Slow Response (30s delay)
- ❌ Bad Request 400 (no reintentable)

📖 **Nota**: El mock de Java es mas estable y simple. Ver [Mock SOAP en Java](src/test/java/com/example/fileprocessor/mock/README.md) para comparar opciones.

## Dependencias Clave

```gradle
// SOAP / JAXB
implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.1")
runtimeOnly("org.glassfish.jaxb:jaxb-runtime:4.0.4")

// Mutation Testing
id("info.solidsoft.pitest") version "1.15.0"
```

## Changelog

### 2026-04-25 - Refactorizacion Mock REST Documentos
- **Refactor:** `DocumentRestMock.java` - Eliminado Jackson, generacion JSON manual
- **Refactor:** `start-document-mock.sh` - Simplificado (mock auto-detecta puerto)
- **Nuevo:** `start-document-mock.bat` - Script Windows para mock REST
- **Doc:** Actualizado `mock/README.md` con documentacion del mock REST

### 2026-04-25 - Nuevo Endpoint de Carga de Documentos
- **Nuevo:** `POST /api/v1/files/load` - Carga documentos desde API REST externa a la BD
- **Nuevo:** `LoadDocumentsUseCase` - Caso de uso para carga de documentos
- **Nuevo:** `LoadDocumentsResult` - DTO de resultado para carga
- **Nuevo:** `DocumentRestGateway.getAllDocuments()` - Puerto para obtener todos los documentos
- **Nuevo:** `DocumentRepository.save(DocumentToProcess)` - Metodo para guardar documentos
- **Nuevo:** `R2dbcDocumentRepository.save()` - Implementacion R2dbc
- **Nuevo:** `DocumentRestMock` - Mock de API REST de documentos con `origin`
- **Nuevo:** `LoadDocumentsUseCaseTest` - Pruebas unitarias
- **Nuevo:** `DocumentRestGatewayImplTest` - Pruebas del adapter
- **Actualizado:** `FileController` con nuevo endpoint `/load`
- **Actualizado:** `DocumentInfo` con campo `origin`
- **Actualizado:** `DocumentRestMock` incluye `origin` en respuestas

### 2026-04-25 - Constantes de Status Centralizadas
- **Nuevo:** `DocumentStatus.java` - Enum con valores `PENDING`, `PROCESSING`, `RETRY`, `SUCCESS`, `FAILURE`, `SKIPPED`
- **Refactor:** `R2dbcDocumentRepository` ahora usa `DocumentStatus.*_VALUE` en SQL
- **Refactor:** `DatabaseInitializer` ahora usa constantes para SQL
- **Refactor:** `ProcessFileUseCase` usa `DocumentStatus.*_VALUE` en lugar de constantes locales
- **Refactor:** `SoapResponse.isSuccess()` usa `DocumentStatus.SUCCESS_VALUE`
- **Refactor:** `FileController` usa `DocumentStatus.PROCESSING_VALUE`
- **Cleanup:** `start-mock.bat` simplificado (el mock ahora auto-detecta puerto)

### 2026-04-22 - Limpieza de Codigo
- **Eliminado**: `SoapConfig.java` - bean `JAXBContext` sin uso
- **Eliminado**: `WebClientConfig.java` - bean `WebClient.Builder` sin uso
- **Eliminado**: `FileUploadResponseDto.java` - DTO sin uso
- **Eliminado**: `DocumentRestGateway.getAllDocuments()` - metodo sin uso
- **Eliminado**: `DocumentRepository.save()` y `findById()` - metodos sin uso
- **Eliminado**: `STATUS_PENDING` constante sin uso en `ProcessFileUseCase.java`

### 2026-04-22 - Configuracion y Documentacion
- **Config**: Agregado `max-file-size-mb: 50` en `application.yml`
- **Doc**: Agregada seccion de JaCoCo coverage reports
- **Doc**: Agregada seccion de SonarQube analysis
- **Doc**: Agregada tabla de `FileUploadProperties`

### 2026-04-21 - Refactorizacion Completa (v4)
- **Seguridad**: Proteccion XXE en `SoapEnvelopeWrapper` (disallow-doctype-decl, external entities bloqueadas)
- **Seguridad**: Eliminado endpoint `/debug` que exponia headers sin autenticacion
- **Defensa**: Validacion temprana de tamano (`Content-Length`) antes de cargar stream en memoria
- **Defensa**: Handler `DataBufferLimitException` con HTTP 413
- **Arquitectura**: Dominio desacoplado de Spring (`@Service`/`@Component` removidos, `DomainConfig` creado)
- **Arquitectura**: Eliminados `FileUploadRequestDto` y `FileDtoMapper` (redundantes con `FileData`)
- **Arquitectura**: Consolidado `JAXBContext` en bean `SoapConfig` compartido
- **Arquitectura**: Eliminado `SoapEnvelope.java`, envelope unificado en `SoapMapper`
- **Arquitectura**: Namespaces SOAP centralizados en `SoapNamespaces`
- **Bugfix**: `onErrorResume(TimeoutException)` ahora captura `retryExhausted` correctamente
- **Bugfix**: Eliminada condicion redundante `|| value == 503` en `isRetryableException`
- **Bugfix**: `SoapMapper.fromSoapXml()` propaga `SoapCommunicationException` ante XML invalido
- **Performance**: `FileValidator` precalcula `Set` de tipos permitidos con `trim()` y `toLowerCase()`
- **Performance**: Eliminado `.subscribeOn(Schedulers.boundedElastic())` innecesario en WebClient
- **Error Handling**: Agregado handler `IllegalStateException` para `Exceptions.retryExhausted`
- **Mutation Testing**: Plugin PIT integrado con threshold del 50%
- **Timeout Dual**: Netty `responseTimeout` desfasado 5s del timeout de Reactor

### 2025-04-21 - Mock SOAP Portable (v3)
- **Nuevo:** `PortableSoapMock.java` - Detecta automaticamente puerto libre (9000, o 9000-9999)
- **Nuevo:** `start-dev.sh` / `start-dev.bat` - Inicia Mock + Microservicio en un solo comando
- **Nuevo:** Scripts en carpeta `scripts/` - Organizados y portables
- **Mejorado:** Auto-deteccion de Java en ubicaciones comunes (sin `JAVA_HOME` requerido)
- **Mejorado:** Guarda configuracion en archivo temporal (`/tmp/file-processor-mock.info`)
- **Solucionado:** Ya no requiere permisos de administrador en Windows
- **Documentacion:** Guia completa en `scripts/README.md`

### 2025-04-20 - Scripts de Mock Mejorados (v2)
- **Mejorado:** Scripts ahora verifican que el puerto realmente se libere antes de reportar exito
- **Agregado:** `start-mock.sh` detecta si el puerto esta ocupado e intenta liberarlo automaticamente
- **Agregado:** `stop-mock.sh` espera y verifica que el proceso realmente haya terminado
- **Agregado:** Scripts `stop-mock.sh` y `stop-mock.bat` para detener el mock SOAP
- **Actualizado:** Documentacion con instrucciones para Windows y Linux/macOS

### 2025-04-20 - Refactor de Codigo
- **Eliminado:** Imports sin uso en `FileControllerTest.java`, `ExternalSoapGatewayImplTest.java` y `GlobalErrorHandler.java`
