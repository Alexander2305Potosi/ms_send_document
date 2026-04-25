# Mocks para Desarrollo

Este directorio contiene dos servidores mock para pruebas:

| Mock | Descripcion | Puerto default |
|------|-------------|-----------------|
| `PortableSoapMock.java` | Mock SOAP con escenarios rotativos | 9000 |
| `DocumentRestMock.java` | Mock REST para documentos | 8081 |

---

# Mock REST Document Server

## Clase Principal: DocumentRestMock.java

El mock REST de documentos funciona en cualquier maquina sin configuracion manual:

- **Auto-detección de Java**: Busca en `JAVA_HOME`, `PATH` y ubicaciones comunes
- **Puerto dinámico**: Intenta 8081, si esta ocupado usa 8081-9999
- **Guarda configuración**: Crea archivo temporal con el endpoint usado
- **Sin dependencias externas**: No usa Jackson, genera JSON manualmente

## Endpoints

| Endpoint | Metodo | Descripcion |
|----------|--------|-------------|
| `/api/documents` | GET | Lista todos los documentos disponibles |
| `/api/document/{id}` | GET | Obtiene un documento especifico por ID |

## Documentos Disponibles

| ID | Nombre | Content-Type | Origin |
|----|--------|--------------|--------|
| doc-001 | test-document.pdf | application/pdf | folderA/incoming |
| doc-002 | test-document.docx | application/vnd.openxmlformats-officedocument.wordprocessingml.document | folderB/incoming |
| doc-003 | test-document.txt | text/plain | folderA/special |

## Uso

### Opcion 1: Script Automatico (Recomendada)

```bash
# Windows
scripts\start-document-mock.bat

# Linux/Mac
chmod +x ./scripts/start-document-mock.sh
./scripts/start-document-mock.sh
```

Ver el endpoint configurado:

```bash
# Windows
type %TEMP%\document-rest-mock.info

# Linux/Mac
cat /tmp/document-rest-mock.info
```

### Opcion 2: Ejecucion Manual con Java

```bash
# Compilar
./gradlew testClasses

# Ejecutar (puerto automatico)
java -cp build/classes/java/test com.example.fileprocessor.mock.DocumentRestMock

# Con puerto especifico
java -cp build/classes/java/test com.example.fileprocessor.mock.DocumentRestMock 8081
```

## Ejemplo de Respuesta

### GET /api/documents

```json
[
  {
    "documentId": "doc-001",
    "filename": "test-document.pdf",
    "content": "JVBERi0x...",
    "contentType": "application/pdf",
    "size": 1024,
    "isZip": false,
    "origin": "folderA/incoming"
  }
]
```

### GET /api/document/doc-001

```json
{
  "documentId": "doc-001",
  "filename": "test-document.pdf",
  "content": "JVBERi0x...",
  "contentType": "application/pdf",
  "size": 1024,
  "isZip": false,
  "origin": "folderA/incoming"
}
```

## Arquitectura

```
Request GET --> HttpServer --> Handler --> JSON Response
                    (puerto dinamico)
```

1. Detecta Java instalado
2. Busca puerto disponible (8081 -> 8081-9999)
3. Crea servidor HTTP en ese puerto
4. Guarda info en archivo temporal
5. Responde JSON estatico

---

# Mock SOAP Server en Java

## Clase Principal: PortableSoapMock.java

El mock SOAP portable funciona en cualquier máquina sin configuración manual:

- **Auto-detección de Java**: Busca en `JAVA_HOME`, `PATH` y ubicaciones comunes
- **Puerto dinámico**: Intenta 9000, si está ocupado usa 9000-9999
- **Guarda configuración**: Crea archivo temporal con el endpoint usado
- **Cross-platform**: Mismo código funciona en Windows, Linux y macOS

## Cómo funciona

```
Request POST ──> HttpServer ──> Handler ──> Respuesta XML
                              (puerto dinámico)
```

1. Detecta Java instalado
2. Busca puerto disponible (9000 → 9000-9999)
3. Crea servidor HTTP en ese puerto
4. Guarda info en archivo temporal
5. Responde SOAP XML estático

## Uso

### Opción 1: Script Automático (Recomendada)

Desde la raíz del proyecto:

```bash
# Windows
start-dev.bat

# Linux/Mac
./start-dev.sh
```

Esto inicia el Mock + Microservicio con configuración automática.

### Opción 2: Solo el Mock

```bash
# Windows
scripts\start-mock.bat

# Linux/Mac
./scripts/start-mock.sh
```

Ver el endpoint configurado:

```bash
# Windows
type %TEMP%\file-processor-mock.info

# Linux/Mac
cat /tmp/file-processor-mock.info
```

Salida ejemplo:
```
port=9001
endpoint=http://localhost:9001/soap/fileservice
timestamp=...
```

### Opción 3: Ejecución Manual con Java

```bash
# Compilar
./gradlew testClasses

# Ejecutar (puerto automático, todos los escenarios)
java -cp build/classes/java/test com.example.fileprocessor.mock.PortableSoapMock

# Con puerto específico
java -cp build/classes/java/test com.example.fileprocessor.mock.PortableSoapMock 9000

# Solo escenarios específicos (ej: solo 200 y 500)
java -cp build/classes/java/test com.example.fileprocessor.mock.PortableSoapMock 9000 1,2
```

## Escenarios de Respuesta

El mock rota **infinitamente** entre los escenarios activos. Cada peticion recibe el siguiente escenario en orden:

| # | Escenario | HTTP Status | Delay | Descripcion |
|---|-----------|-------------|-------|-------------|
| 1 | **Success** | 200 | 100ms | Respuesta exitosa con `status=SUCCESS` |
| 2 | **Server Error 500** | 500 | 100ms | `soap:Fault` con error temporal (reintentable) |
| 3 | **Service Unavailable 503** | 503 | 100ms | `soap:Fault` con header `Retry-After: 30` (reintentable) |
| 4 | **Gateway Timeout 504** | 504 | 100ms | `soap:Fault` con timeout (reintentable) |
| 5 | **Slow Response** | 200 | **30s** | Respuesta exitosa con delay de 30 segundos |
| 6 | **Bad Request 400** | 400 | 100ms | `soap:Fault` con error de cliente (no reintentable) |

Despues del escenario 6, vuelve al 1 automaticamente.

### Ejemplo de respuesta exitosa

```xml
<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:file="http://example.com/fileservice">
   <soap:Header/>
   <soap:Body>
      <file:UploadFileResponse>
         <file:status>SUCCESS</file:status>
         <file:message>File uploaded and processed successfully</file:message>
         <file:correlationId>corr-test-12345</file:correlationId>
         <file:processedAt>2024-04-20T12:00:00Z</file:processedAt>
         <file:externalReference>ext-ref-mock-001</file:externalReference>
      </file:UploadFileResponse>
   </soap:Body>
</soap:Envelope>
```

## Filtrar Escenarios

Puedes ejecutar el mock respondiendo **solo los escenarios que necesites**. Esto es util cuando quieres validar un comportamiento específico del microservicio sin tener que esperar la rotacion completa.

### Uso

Pasa los numeros de escenario deseados como **segundo argumento**, separados por comas (sin espacios):

```bash
# Solo exito (200)
java -cp build/classes/java/test com.example.fileprocessor.mock.PortableSoapMock 9000 1

# Solo errores reintentables (500, 503, 504)
java -cp build/classes/java/test com.example.fileprocessor.mock.PortableSoapMock 9000 2,3,4

# Exito y respuesta lenta (200 + 30s delay)
java -cp build/classes/java/test com.example.fileprocessor.mock.PortableSoapMock 9000 1,5
```

También funciona con los scripts portables:

```bash
# Linux/Mac
./scripts/start-mock.sh 9000 1,2

# Windows
scripts\start-mock.bat 9000 1,2
```

### Mapeo de Escenarios

| # | Escenario | HTTP Status | Delay |
|---|-----------|-------------|-------|
| **1** | 200 Success | 200 | 100ms |
| **2** | 500 Server Error | 500 | 100ms |
| **3** | 503 Service Unavailable | 503 | 100ms |
| **4** | 504 Gateway Timeout | 504 | 100ms |
| **5** | 200 Slow Response | 200 | 30s |
| **6** | 400 Bad Request | 400 | 100ms |

Si omites el segundo argumento, el mock rota entre **todos** los escenarios por defecto.

## Personalización

Para modificar la respuesta SOAP, edita el método `handle()` en `PortableSoapMock.java`:

```java
String responseXml = """
    <?xml version="1.0" encoding="UTF-8"?>
    <soap:Envelope ...>
       <!-- Tu XML personalizado aquí -->
    </soap:Envelope>
    """;
```

## Solución de Problemas

### "No se encontro Java"

Define `JAVA_HOME` manualmente:

```bash
# Windows
set JAVA_HOME=C:\Program Files\Microsoft\OpenJDK\jdk-21

# Linux/Mac
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
```

### "Puerto no disponible"

El mock automáticamente busca en el rango 9000-9999. Si todos están ocupados:

```bash
# Ver puertos usados
# Windows:
netstat -ano | findstr "9000"

# Linux/Mac:
netstat -tuln | grep "900[0-9]"
```

### Mock no responde

```bash
# Verificar que está corriendo
# Windows:
tasklist | findstr "java"

# Linux/Mac:
pgrep -f "PortableSoapMock"

# Probar endpoint
curl -X POST http://localhost:PORT/soap/fileservice \
  -H "Content-Type: text/xml" -d "<test>"
```

### Detener el Mock

```bash
# Windows
scripts\stop-mock.bat

# Linux/Mac
./scripts/stop-mock.sh

# O manualmente
killall java  # Linux/Mac
taskkill /F /IM java.exe  # Windows
```

## Archivos

| Archivo | Descripción |
|---------|-------------|
| `PortableSoapMock.java` | Mock portable con auto-detección de puerto |
| `README.md` | Esta documentación |

Para más detalles, ver `scripts/README.md` en la raíz del proyecto.
