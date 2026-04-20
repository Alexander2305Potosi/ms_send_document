# Prueba Rápida - File Processor Service

## Verificar que todo funciona

### 1. Iniciar el Mock SOAP

**Linux/macOS:**
```bash
./start-mock.sh
```

**Windows:**
```cmd
start-mock.bat
```

### 2. Verificar que el servicio responde

En otra terminal:
```bash
curl http://localhost:8080/actuator/health
```

Debería retornar: `{"status":"UP"}`

### 3. Probar subida de archivo

```bash
curl -X POST http://localhost:8080/api/v1/files -F "file=@postman/samples/sample.txt" -H "Accept: application/json"
```

Respuesta esperada:
```json
{"status":"SUCCESS","message":"File processed successfully","correlationId":"corr-test-12345","success":true}
```

---

## Si curl funciona pero Postman no

El problema es la configuración de Postman. Sigue estos pasos:

1. **Abre la petición** "Upload File - TXT" en Postman

2. **Ve a la pestaña Body** y verifica:
   - Esté seleccionado `form-data`
   - La key sea `file`
   - El tipo sea `File` (no Text)
   - Hayas seleccionado un archivo en la columna Value

3. **Ve a la pestaña Headers** y verifica:
   - NO haya un header `Content-Type`
   - Solo esté `Accept: application/json`

4. **Haz clic en Send**

---

## URLs de los servicios

| Servicio | URL |
|----------|-----|
| File Processor | http://localhost:8080/api/v1/files |
| Health Check | http://localhost:8080/actuator/health |
| Mock SOAP | http://localhost:8081/soap/fileservice |

---

## Solución de problemas

### Error "No file provided with key 'file'"
- Postman no está enviando el archivo
- Verifica que en Body esté seleccionado "form-data"
- Verifica que el campo "file" tenga tipo "File"
- Asegúrate de haber seleccionado un archivo

### Error "Connection refused"
- El servicio Spring Boot no está corriendo
- Inicia con: `./gradlew bootRun --args='--spring.profiles.active=dev'`

### Error "SOAP timeout"
- El mock SOAP no está corriendo
- Inicia con: `./start-mock.sh` (Linux/Mac) o `start-mock.bat` (Windows)

### Error "SOAP error response: 404 Not Found"
- La variable `SOAP_ENDPOINT` no está configurada o está mal configurada
- Verifica: `echo $SOAP_ENDPOINT` (Linux/Mac) o `echo %SOAP_ENDPOINT%` (Windows)
- Debe ser: `http://localhost:8081/soap/fileservice`
- En Windows: Configura la variable en Propiedades del Sistema → Variables de entorno

### Error "Address already in use: 8081"
- El puerto 8081 está ocupado (posiblemente por SoapUI u otra instancia del mock)
- Detén el mock anterior: `./stop-mock.sh` (Linux/Mac) o `stop-mock.bat` (Windows)
- O cambia el puerto en `application.yml`
