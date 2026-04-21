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

### Configuración Automática (Recomendada)

Al usar los scripts portables (`start-dev.bat` / `start-dev.sh`), la variable se configura automáticamente:

```bash
./start-dev.sh  # Detecta el puerto y configura SOAP_ENDPOINT
```

### Configuración Manual

Si ejecutas manualmente, obtén el endpoint del mock:

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

### Configuración Fija (Sin Mock Variable)

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

Para desarrollo y pruebas locales, incluye mocks SOAP que simulan el servicio externo:

### Iniciar el Mock SOAP (Versión Portable - Recomendada)

**Nuevo:** Los scripts portables detectan automáticamente Java y un puerto disponible (no requiere admin).

**Windows:**
```cmd
# Opción 1: Script completo (Mock + Microservicio)
start-dev.bat

# Opción 2: Solo el Mock
scripts\start-mock.bat

# Ver que puerto se usó:
type %TEMP%\file-processor-mock.info
```

**Linux/macOS:**
```bash
# Opción 1: Script completo (Mock + Microservicio)
chmod +x start-dev.sh
./start-dev.sh

# Opción 2: Solo el Mock
./scripts/start-mock.sh

# Ver que puerto se usó:
cat /tmp/file-processor-mock.info
```

El mock detecta automáticamente:
- **Java**: Busca en ubicaciones comunes (`JAVA_HOME`, `PATH`, etc.)
- **Puerto libre**: Intenta 8081, si está ocupado usa 9000-9999
- **Configuración**: Guarda el endpoint en archivo temporal

### Ejecución Manual con Puerto Específico

Si necesitas un puerto fijo (ej: 9000), ejecuta directamente con Java:

```bash
# Compilar
./gradlew testClasses

# Iniciar con puerto específico
java -cp build/classes/java/test com.example.fileprocessor.mock.PortableSoapMock 9000

# Configurar endpoint manualmente
export SOAP_ENDPOINT=http://localhost:9000/soap/fileservice
```

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

### Scripts Principales (Nuevos - Portable)

| Script | Plataforma | Descripción |
|--------|------------|-------------|
| `./start-dev.sh` | Linux/Mac | **Inicia Mock + Microservicio** (auto-configura puerto) |
| `start-dev.bat` | Windows | **Inicia Mock + Microservicio** (auto-configura puerto) |
| `./scripts/start-mock.sh` | Linux/Mac | Mock portable (auto-detecta Java y puerto libre) |
| `scripts\start-mock.bat` | Windows | Mock portable (auto-detecta Java y puerto libre) |
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

📖 **Nota**: El mock de Java es más estable y simple. Ver [Mock SOAP en Java](src/test/java/com/example/fileprocessor/mock/README.md) para comparar opciones.

## Dependencias Clave

```gradle
// SOAP / JAXB
implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.1")
runtimeOnly("org.glassfish.jaxb:jaxb-runtime:4.0.4")
```

## Changelog

### 2025-04-21 - Mock SOAP Portable (v3) 🎉
- **Nuevo:** `PortableSoapMock.java` - Detecta automáticamente puerto libre (8081, o 9000-9999)
- **Nuevo:** `start-dev.sh` / `start-dev.bat` - Inicia Mock + Microservicio en un solo comando
- **Nuevo:** Scripts en carpeta `scripts/` - Organizados y portables
- **Mejorado:** Auto-detección de Java en ubicaciones comunes (sin `JAVA_HOME` requerido)
- **Mejorado:** Guarda configuración en archivo temporal (`/tmp/file-processor-mock.info`)
- **Solucionado:** Ya no requiere permisos de administrador en Windows
- **Documentación:** Guía completa en `scripts/README.md`

### 2025-04-20 - Scripts de Mock Mejorados (v2)
- **Mejorado:** Scripts ahora verifican que el puerto realmente se libere antes de reportar éxito
- **Agregado:** `start-mock.sh` detecta si el puerto está ocupado e intenta liberarlo automáticamente
- **Agregado:** `stop-mock.sh` espera y verifica que el proceso realmente haya terminado
- **Agregado:** Scripts `stop-mock.sh` y `stop-mock.bat` para detener el mock SOAP
- **Actualizado:** Documentación con instrucciones para Windows y Linux/macOS

### 2025-04-20 - Refactor de Código
- **Eliminado:** Imports sin usar en `FileControllerTest.java`, `ExternalSoapGatewayImplTest.java` y `GlobalErrorHandler.java`
