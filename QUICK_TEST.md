# Prueba Rápida - File Processor Service

Guía rápida para ejecutar el proyecto en modo desarrollo con el Mock SOAP.

## Opción 1: Script Automático (Recomendada) ⭐

Un solo comando inicia todo (Mock + Microservicio):

### Windows

```cmd
# Desde el directorio raiz del proyecto
start-dev.bat
```

### Linux/macOS

```bash
# Desde el directorio raiz del proyecto
chmod +x start-dev.sh
./start-dev.sh
```

Esto hará:
1. Detectar Java instalado
2. Buscar un puerto disponible (8081 o 9000-9999)
3. Iniciar el Mock SOAP
4. Configurar automáticamente `SOAP_ENDPOINT`
5. Iniciar el microservicio

**Listo para probar en Postman!**

---

## Opción 2: Manual (Mayor control)

### 1. Iniciar el Mock SOAP

**Windows:**
```cmd
scripts\start-mock.bat

# Ver en que puerto quedo:
type %TEMP%\file-processor-mock.info
```

**Linux/macOS:**
```bash
./scripts/start-mock.sh

# Ver en que puerto quedo:
cat /tmp/file-processor-mock.info
```

Deberías ver:
```
========================================
  SOAP Mock Server (Portable)
========================================
  Puerto: 9001  (o 8081 si estaba libre)
  Endpoint: http://localhost:9001/soap/fileservice
========================================
```

### 2. Configurar Endpoint

El script `start-mock` guarda el endpoint usado. Configúralo:

**Windows:**
```cmd
for /f "tokens=2 delims==" %a in ('type %TEMP%\file-processor-mock.info ^| findstr "endpoint"') do set SOAP_ENDPOINT=%a

echo %SOAP_ENDPOINT%
```

**Linux/macOS:**
```bash
export SOAP_ENDPOINT=$(cat /tmp/file-processor-mock.info | grep endpoint | cut -d= -f2)

echo $SOAP_ENDPOINT
```

### 3. Iniciar Microservicio

```bash
# Windows
gradlew.bat bootRun --args='--spring.profiles.active=dev'

# Linux/Mac
./gradlew bootRun --args='--spring.profiles.active=dev'
```

---

## URLs de los Servicios

| Servicio | URL |
|----------|-----|
| File Processor | http://localhost:8080/api/v1/files |
| Health Check | http://localhost:8080/actuator/health |
| Mock SOAP | Ver archivo `file-processor-mock.info` |

---

## Probar con cURL

```bash
# Subir archivo (ajusta la ruta)
curl -X POST http://localhost:8080/api/v1/files \
  -F "file=@postman/samples/sample.txt" \
  -H "Accept: application/json"
```

Respuesta esperada:
```json
{
  "status": "SUCCESS",
  "message": "File processed successfully",
  "correlationId": "corr-test-12345",
  "success": true
}
```

---

## Solución de Problemas

### "No se encontro Java"

Define manualmente antes de ejecutar:

**Windows:**
```cmd
set JAVA_HOME=C:\Program Files\Microsoft\OpenJDK\jdk-21
```

**Linux/Mac:**
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
```

### Error "Connection refused"

El microservicio no encuentra el mock. Verifica:
1. El mock está corriendo: `cat /tmp/file-processor-mock.info` (Linux/Mac) o `type %TEMP%\file-processor-mock.info` (Windows)
2. La variable `SOAP_ENDPOINT` está configurada correctamente

### Error "SOAP error response: 404"

El microservicio apunta a un endpoint incorrecto. Asegúrate de:
1. Configurar `SOAP_ENDPOINT` antes de iniciar el microservicio
2. Usar el puerto correcto que muestra el mock

### Puerto ocupado en Windows (sin permisos admin)

El script portable automáticamente busca otro puerto (9000-9999). Si falla:

```cmd
# Ver que puertos están usados
netstat -ano | findstr "9000"

# O reiniciar la computadora para liberar todos los puertos
```

---

## Estructura de Archivos

```
file-processor-service/
├── start-dev.sh              # Script completo (Linux/Mac) ⭐
├── start-dev.bat             # Script completo (Windows) ⭐
├── scripts/
│   ├── start-mock.sh         # Solo Mock (Linux/Mac)
│   ├── start-mock.bat        # Solo Mock (Windows)
│   ├── stop-mock.sh          # Detener Mock (Linux/Mac)
│   ├── stop-mock.bat         # Detener Mock (Windows)
│   └── README.md             # Documentación completa
├── src/test/java/...
│   └── PortableSoapMock.java # Clase Java portable
└── ...
```

---

**Para más detalles:** Ver `scripts/README.md`
