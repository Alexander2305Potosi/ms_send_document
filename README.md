# File Processor Service

Microservicio reactivo basado en Spring WebFlux que recibe archivos vía multipart y los envía a un servicio SOAP externo.

## Arquitectura (Clean Architecture)

El proyecto sigue **Clean Architecture** con las siguientes capas:

```
com.example.fileprocessor/
├── domain/                    # Capa de dominio (independiente de frameworks)
│   ├── entity/               # Entidades de negocio
│   │   ├── FileData.java
│   │   ├── SoapRequest.java
│   │   └── SoapResponse.java
│   ├── port/out/             # Puertos de salida (interfaces)
│   │   └── ExternalSoapGateway.java
│   └── exception/            # Excepciones de dominio
│       ├── DomainException.java
│       └── FileValidationException.java
│
├── application/               # Capa de aplicación (casos de uso)
│   ├── usecase/              
│   │   ├── ProcessFileUseCase.java
│   │   └── FileValidator.java
│   ├── dto/                  # DTOs de aplicación
│   │   └── FileUploadResponseDto.java
│   ├── mapper/               # Mappers de aplicación
│   │   └── FileMapper.java
│   └── config/               # Configuración de aplicación
│       └── FileUploadProperties.java
│
└── infrastructure/            # Capa de infraestructura (adapters)
    ├── rest/                  # Adapter REST (entrada)
    │   ├── controller/
    │   │   └── FileController.java
    │   ├── dto/
    │   │   └── FileUploadRequestDto.java
    │   ├── mapper/
    │   │   └── FileDtoMapper.java
    │   └── exception/
    │       └── GlobalErrorHandler.java
    ├── soap/                  # Adapter SOAP (salida)
    │   ├── adapter/
    │   │   └── ExternalSoapGatewayImpl.java
    │   ├── config/
    │   │   └── SoapProperties.java
    │   ├── mapper/
    │   │   └── SoapMapper.java
    │   ├── xml/
    │   │   ├── SoapEnvelopeWrapper.java
    │   │   └── model/
    │   │       ├── UploadFileRequest.java   # JAXB Model
    │   │       └── UploadFileResponse.java  # JAXB Model
    │   └── exception/
    │       └── SoapCommunicationException.java
    └── config/                # Configuración global
        └── WebClientConfig.java
```

### Reglas de Dependencia

- **Domain** no depende de ninguna otra capa
- **Application** solo depende de Domain
- **Infrastructure** depende de Application y Domain

## JAXB - Serialización SOAP

El proyecto utiliza **Jakarta XML Binding (JAXB)** para la serialización/deserialización de mensajes SOAP:

### Ventajas

- **Tipado fuerte**: Clases Java con anotaciones XML
- **Validación automática**: Esquemas XML validados en runtime
- **Mantenibilidad**: Cambios en el modelo son refactorizaciones seguras
- **Namespace handling**: Soporte completo de namespaces SOAP

### Ejemplo de Request JAXB

```java
@XmlRootElement(name = "UploadFileRequest", namespace = "http://example.com/fileservice")
@XmlAccessorType(XmlAccessType.FIELD)
public class UploadFileRequest {
    @XmlElement(name = "content", namespace = "http://example.com/fileservice", required = true)
    private String content;
    // ...
}
```

### Generación del XML

```java
// Marshalling (Java -> XML)
UploadFileRequest request = new UploadFileRequest(content, filename, ...);
String soapXml = envelopeWrapper.wrapRequest(request);

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

## Requisitos Previos

- **Java 21**
- **Docker** (opcional)
- **Gradle 8.5+**

## Compilación

```bash
./gradlew clean build
```

## Ejecución de Tests

```bash
# Tests unitarios e integración
./gradlew test

# Reporte se genera en:
# build/reports/tests/test/index.html
```

## Ejecución del Servicio

```bash
# Desarrollo (con perfil dev)
./gradlew bootRun --args='--spring.profiles.active=dev'

# Producción
./gradlew bootRun --args='--spring.profiles.active=prod'
```

## Variables de Entorno

| Variable | Descripción | Default |
|----------|-------------|---------|
| `SOAP_ENDPOINT` | URL del servicio SOAP | `http://localhost:8081/soap/fileservice` |

### Configuración en Linux/macOS

```bash
export SOAP_ENDPOINT=http://localhost:8081/soap/fileservice
./gradlew bootRun
```

### Configuración en Windows

```cmd
set SOAP_ENDPOINT=http://localhost:8081/soap/fileservice
gradlew.bat bootRun
```

O permanentemente en Variables de Entorno del Sistema → Variables de usuario → Nuevo:
- Nombre: `SOAP_ENDPOINT`
- Valor: `http://localhost:8081/soap/fileservice`

## Mock SOAP para desarrollo

Para desarrollo y pruebas locales, incluye mocks SOAP que simulan el servicio externo:

### Iniciar el Mock SOAP

**Linux/macOS:**
```bash
# Mock simple (siempre responde éxito 200)
./start-mock.sh

# Mock avanzado (múltiples respuestas: 200, 500, 503, 504, delay, 400)
./start-advanced-mock.sh

# Detener el mock
./stop-mock.sh
```

**Windows:**
```cmd
# Mock simple
start-mock.bat

# Detener el mock
stop-mock.bat
```

El mock se iniciará en `http://localhost:8081/soap/fileservice`

### Más información

Para detalles sobre los mocks disponibles y cómo personalizarlos, ver:
- [`src/test/java/com/example/fileprocessor/mock/README.md`](src/test/java/com/example/fileprocessor/mock/README.md) - Documentación completa de los mocks
- [`soapui/README.md`](soapui/README.md) - Alternativa usando SOAP UI

## API Endpoints

### POST /api/v1/files

Sube un archivo y lo envía al servicio SOAP.

**Request:**
```bash
curl -X POST http://localhost:8080/api/v1/files \
  -F "file=@/path/to/document.pdf" \
  -H "Content-Type: multipart/form-data"
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

## Restricciones de Archivos

- **Tamaño máximo**: 10 MB
- **Tipos permitidos**: `pdf`, `docx`, `txt`
- **Nombre máximo**: 255 caracteres

## Reintentos SOAP

El servicio implementa **3 reintentos máximos** con backoff exponencial:

- **Escenarios reintentables**: Timeouts, errores 5xx (500, 502, 503, 504)
- **Delay**: 1s, 2s, 4s entre intentos
- **Logging**: Cada reintento es loggeado con traceId

```
INFO  - Sending SOAP request for traceId: abc-123, maxRetries=3
WARN  - Retrying SOAP call for traceId=abc-123, attempt 1/3 (backoff=1000ms)
WARN  - Retrying SOAP call for traceId=abc-123, attempt 2/3 (backoff=2000ms)
WARN  - Retrying SOAP call for traceId=abc-123, attempt 3/3 (backoff=4000ms)
ERROR - SOAP timeout for traceId: abc-123 after all retries exhausted
```

## Testing con Postman

Ver carpeta `postman/`:

```bash
# Importar en Postman
postman/File-Processor-Service.postman_collection.json
```

Endpoints incluidos:
- Upload File PDF/DOCX/TXT
- Health Check
- Error scenarios (400, 504)

⚠️ **Importante**: Después de importar, selecciona manualmente el archivo en la pestaña Body → form-data.

Para más detalles: [`postman/README.md`](postman/README.md)

## Scripts Disponibles

| Script | Plataforma | Descripción |
|--------|------------|-------------|
| `./gradlew bootRun` | Todos | Iniciar el servicio Spring Boot |
| `./start-mock.sh` | Linux/Mac | Iniciar mock SOAP simple |
| `start-mock.bat` | Windows | Iniciar mock SOAP simple |
| `./start-advanced-mock.sh` | Linux/Mac | Iniciar mock con múltiples respuestas |
| `./stop-mock.sh` | Linux/Mac | Detener el mock SOAP |
| `stop-mock.bat` | Windows | Detener el mock SOAP |

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

📖 **Nota**: El mock de Java es más estable y simple. Ver [Mock SOAP en Java](src/test/java/com/example/fileprocessor/mock/README.md) para comparar opciones.

## Dependencias Clave

```gradle
// SOAP / JAXB
implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.1")
runtimeOnly("org.glassfish.jaxb:jaxb-runtime:4.0.4")
```

## Changelog

### 2025-04-20 - Scripts de Mock Mejorados
- **Agregado:** Scripts `stop-mock.sh` y `stop-mock.bat` para detener el mock SOAP fácilmente
- **Agregado:** `start-mock.sh` ahora guarda el PID para facilitar la detención
- **Actualizado:** Documentación con instrucciones para Windows y Linux/macOS
- **Agregado:** Sección de troubleshooting con errores comunes (puerto ocupado, 404, timeout)

### 2025-04-20 - Refactor de Código
- **Eliminado:** Imports sin usar en `FileControllerTest.java`, `ExternalSoapGatewayImplTest.java` y `GlobalErrorHandler.java`
