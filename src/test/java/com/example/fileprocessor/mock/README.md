# Mock SOAP Servers en Java

## ¿Por qué funciona el mock Java y no SOAP UI?

### Mock Java (SimpleSoapMock)
✅ **Ventajas:**
- No requiere configuración de WSDL
- Acepta cualquier POST en el endpoint
- Solo devuelve XML estático
- No valida el SOAPAction
- Funciona inmediatamente

### SOAP UI
❌ **Problemas:**
- Requiere WSDL válido y bien configurado
- Valida el SOAPAction contra el WSDL
- El proyecto debe estar perfectamente vinculado
- Puede dar errores de "Missing MockResponse"

## Cómo funciona

```
Request POST ──> HttpServer ──> Handler ──> Respuesta XML
```

El mock Java simplemente:
1. Crea un servidor HTTP en el puerto 8081
2. Escucha POST en `/soap/fileservice`
3. Devuelve el XML que definimos

No valida nada del request, solo responde.

## Archivos disponibles

| Archivo | Descripción | Uso |
|---------|-------------|-----|
| `SimpleSoapMock.java` | Mock básico - solo éxito | `./start-mock.sh` |
| `AdvancedSoapMock.java` | Mock avanzado - múltiples respuestas | `./start-advanced-mock.sh` |

## Mock Avanzado - Respuestas en Secuencia

El `AdvancedSoapMock` rota entre 6 respuestas:

| # | Código | Descripción |
|---|--------|-------------|
| 1 | 200 | Éxito normal |
| 2 | 500 | Error de servidor (reintentable) |
| 3 | 503 | Servicio no disponible (reintentable) |
| 4 | 504 | Gateway timeout (reintentable) |
| 5 | 200 | Éxito con delay de 5 segundos |
| 6 | 400 | Bad Request (no reintentable) |

### Para agregar más respuestas

Edita `AdvancedSoapMock.java`:

1. **Cambiar el número de respuestas:**
   ```java
   int responseType = ((count - 1) % 6) + 1; // Cambia el 6 por el nuevo total
   ```

2. **Agregar nuevo case en el switch:**
   ```java
   case 7 -> sendCustomResponse(exchange);
   ```

3. **Crear el método de respuesta:**
   ```java
   private void sendCustomResponse(HttpExchange exchange) throws IOException {
       String responseXml = """
           <?xml version="1.0" encoding="UTF-8"?>
           <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Header/>
              <soap:Body>
                 <soap:Fault>
                    <faultcode>soap:Server</faultcode>
                    <faultstring>Mi error personalizado</faultstring>
                 </soap:Fault>
              </soap:Body>
           </soap:Envelope>
           """;
       sendResponse(exchange, 418, responseXml); // Código HTTP que quieras
       System.out.println("[RESPONSE] 418 I'm a teapot");
   }
   ```

## Ejecución

### Linux/macOS

**Mock simple (solo éxito):**
```bash
./start-mock.sh
```

**Mock avanzado (múltiples respuestas):**
```bash
./start-advanced-mock.sh
```

**Detener el mock:**
```bash
./stop-mock.sh
```

### Windows

**Mock simple:**
```cmd
start-mock.bat
```

**Detener el mock:**
```cmd
stop-mock.bat
```

## Personalización rápida

### Cambiar el puerto:
```java
HttpServer server = HttpServer.create(new InetSocketAddress(8082), 0);
```

### Cambiar el path:
```java
server.createContext("/mi/ruta/personalizada", new SoapHandler());
```

### Agregar delay a todas las respuestas:
```java
Thread.sleep(2000); // 2 segundos antes de cada respuesta
```

## Solución de Problemas

### Error "Address already in use: 8081"
**Causa:** El puerto 8081 está ocupado (otro mock, SoapUI, etc.)

**Solución:**
```bash
# Linux/macOS - usar el script de detención
./stop-mock.sh

# O manualmente
lsof -ti:8081 | xargs kill -9

# Windows
taskkill /F /IM java.exe
# o
stop-mock.bat
```

### Error "Connection refused" al subir archivo
**Causa:** El mock no está corriendo o está en otro puerto

**Solución:**
1. Verificar que el mock está corriendo: `curl http://localhost:8081/soap/fileservice`
2. Verificar la variable `SOAP_ENDPOINT`:
   - Linux/Mac: `echo $SOAP_ENDPOINT`
   - Windows: `echo %SOAP_ENDPOINT%`
3. Debe ser: `http://localhost:8081/soap/fileservice`
