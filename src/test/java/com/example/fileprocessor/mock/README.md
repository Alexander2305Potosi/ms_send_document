# Mock SOAP Server en Java

## Clase Principal: PortableSoapMock.java

El mock SOAP portable funciona en cualquier máquina sin configuración manual:

- **Auto-detección de Java**: Busca en `JAVA_HOME`, `PATH` y ubicaciones comunes
- **Puerto dinámico**: Intenta 8081, si está ocupado usa 9000-9999
- **Guarda configuración**: Crea archivo temporal con el endpoint usado
- **Cross-platform**: Mismo código funciona en Windows, Linux y macOS

## Cómo funciona

```
Request POST ──> HttpServer ──> Handler ──> Respuesta XML
                              (puerto dinámico)
```

1. Detecta Java instalado
2. Busca puerto disponible (8081 → 9000-9999)
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

# Ejecutar (puerto automático)
java -cp build/classes/java/test com.example.fileprocessor.mock.PortableSoapMock

# O con puerto específico
java -cp build/classes/java/test com.example.fileprocessor.mock.PortableSoapMock 9000
```

## Respuesta del Mock

Siempre devuelve éxito (HTTP 200):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:file="http://example.com/fileservice">
   <soap:Header/>
   <soap:Body>
      <file:UploadFileResponse>
         <file:status>SUCCESS</file:status>
         <file:message>File processed successfully</file:message>
         <file:correlationId>corr-test-12345</file:correlationId>
         <file:processedAt>2024-04-20T12:00:00Z</file:processedAt>
         <file:externalReference>ext-ref-mock-001</file:externalReference>
      </file:UploadFileResponse>
   </soap:Body>
</soap:Envelope>
```

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
