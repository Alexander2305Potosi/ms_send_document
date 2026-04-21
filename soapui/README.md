# SOAP UI Mock Service

## Archivos incluidos

- `FileService-Mock-soapui-project.xml` - Proyecto SOAP UI con WSDL embebido
- `FileService.wsdl` - WSDL del servicio (referencia)

## Requisitos

- [SoapUI](https://www.soapui.org/downloads/latest-release/) (Open Source o ReadyAPI)

## Instrucciones de uso

### 1. Importar el proyecto

1. Abre **SoapUI**
2. Ve a **File** → **Import Project**
3. Selecciona `FileService-Mock-soapui-project.xml`
4. El proyecto se cargara con el WSDL embebido (no requiere URL externa)

### 2. Iniciar el Mock Service

1. En el panel izquierdo, expande el proyecto **FileService SOAP Mock**
2. Expande **Mock Services** → haz clic derecho en **FileService Mock** → **Start**
3. El mock se iniciara en `http://localhost:8081/soap/fileservice`

### 3. Verificar que funciona

Desde tu terminal o Postman, envia una peticion SOAP al endpoint:

```bash
curl -X POST http://localhost:8081/soap/fileservice \
  -H "Content-Type: text/xml" \
  -H "SOAPAction: http://example.com/fileservice/UploadFile" \
  -d '<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:file="http://example.com/fileservice">
  <soap:Header/>
  <soap:Body>
    <file:UploadFileRequest>
      <file:content>dGVzdA==</file:content>
      <file:filename>test.pdf</file:filename>
      <file:contentType>application/pdf</file:contentType>
      <file:fileSize>4</file:fileSize>
      <file:traceId>trace-test-123</file:traceId>
      <file:timestamp>2024-04-20T12:00:00Z</file:timestamp>
    </file:UploadFileRequest>
  </soap:Body>
</soap:Envelope>'
```

### 4. Cambiar el puerto (opcional)

Si el puerto 8081 esta ocupado:

1. Haz doble clic en **FileService Mock**
2. Modifica el campo **Port** al puerto deseado (ej: `9000`)
3. Reinicia el mock

## Respuestas disponibles

El mock esta configurado con dispatch **SEQUENCE**, lo que significa que las respuestas se devuelven en orden:

| # | Respuesta | HTTP Status | Delay | Descripcion |
|---|-----------|-------------|-------|-------------|
| 1 | **Success Response** | 200 | 100ms | Respuesta exitosa con `status=SUCCEES` |
| 2 | **Server Error 500** | 500 | 100ms | `soap:Fault` con error de servidor |
| 3 | **Service Unavailable 503** | 503 | 100ms | `soap:Fault` con header `Retry-After: 30` |
| 4 | **Gateway Timeout 504** | 504 | 100ms | `soap:Fault` con timeout de gateway |
| 5 | **Slow Response** | 200 | **30s** | Respuesta exitosa pero con delay de 30 segundos |
| 6 | **Bad Request 400** | 400 | 100ms | `soap:Fault` con error de cliente |

### Reiniciar la secuencia

Para volver a la primera respuesta, haz clic derecho en **FileService Mock** → **Restart**.

## Solucion de problemas

### "Cannot invoke WsdlOperation.getAction() because wsdlOperation is null"

**Causa**: El proyecto no tiene el WSDL embebido correctamente.

**Solucion**: Usa el archivo actualizado `FileService-Mock-soapui-project.xml` que incluye el WSDL embebido en la etiqueta `<con:definitionCache>`.

### "Port 8081 already in use"

**Causa**: Otro proceso (incluyendo el Mock Java) esta usando el puerto 8081.

**Solucion**: Cambia el puerto del mock en SoapUI o deten el otro servicio.

### "Connection refused"

**Causa**: El mock service no esta iniciado.

**Solucion**: Asegurate de hacer clic derecho → **Start** en el mock service antes de enviar peticiones.

## Diferencias con el Mock Java

| Caracteristica | SOAP UI Mock | Mock Java (PortableSoapMock) |
|---------------|--------------|------------------------------|
| Configuracion | WSDL embebido | Sin configuracion necesaria |
| Respuestas | 6 escenarios (secuencia) | Solo exito (200) |
| Escenarios de error | Si (500, 503, 504, 400) | No - solo exito |
| Delay configurable | Si (por respuesta) | No - fijo |
| Requiere instalacion | SoapUI | Solo Java |
| Puerto configurable | Si (por UI) | Automatico (8081 o 9000-9999) |

## Recomendacion

- Usa **SoapUI** si necesitas probar escenarios de error especificos (500, 503, 504, 400) o delays largos
- Usa el **Mock Java** (`PortableSoapMock`) para pruebas rapidas y automatizadas sin dependencias extra
