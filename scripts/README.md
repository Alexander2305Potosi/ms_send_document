# Scripts Portables para Mock SOAP

Estos scripts funcionan en cualquier máquina sin configuración manual, detectando automáticamente:
- Java instalado (busca en ubicaciones comunes)
- Puertos disponibles (si 8081 está ocupado, usa 9000-9999)
- Plataforma (Windows `.bat` / Unix `.sh`)

## Archivos

| Archivo | Plataforma | Descripción |
|---------|------------|-------------|
| `start-mock.sh` | Linux/macOS | Inicia mock, busca puerto libre |
| `start-mock.bat` | Windows | Inicia mock, busca puerto libre |
| `stop-mock.sh` | Linux/macOS | Detiene cualquier mock activo |
| `stop-mock.bat` | Windows | Detiene cualquier mock activo |
| `PortableSoapMock.java` | Todas | Clase Java con auto-detección de puerto |

## Uso Rápido

### Windows

```cmd
# Ir a la carpeta scripts
cd scripts

# Iniciar mock (detecta Java y puerto automáticamente)
start-mock.bat

# En otra terminal, ver que puerto se usó:
type %TEMP%\file-processor-mock.info

# El archivo mostrará:
# port=9001
# endpoint=http://localhost:9001/soap/fileservice
```

### Linux/macOS

```bash
# Dar permisos (primera vez)
chmod +x scripts/*.sh

# Iniciar mock
./scripts/start-mock.sh

# Ver puerto usado
cat /tmp/file-processor-mock.info
```

## Configuración del Microservicio

El mock guarda su información en un archivo temporal. Configura automáticamente el endpoint:

### Opción A: Script de inicio automático (Recomendada)

Crea `start-dev.bat` (Windows) o `start-dev.sh` (Linux/Mac):

**Windows (`start-dev.bat`):**
```batch
@echo off
cd /d "%~dp0"

REM Iniciar mock y obtener puerto
start /B scripts\start-mock.bat

timeout /t 3 /nobreak >nul

REM Leer puerto del archivo temporal
for /f "tokens=2 delims==" %%a in ('type %TEMP%\file-processor-mock.info ^| findstr "endpoint"') do (
    set SOAP_ENDPOINT=%%a
)

echo Usando endpoint: %SOAP_ENDPOINT%

REM Iniciar microservicio
gradlew.bat bootRun --args='--spring.profiles.active=dev'
```

**Linux/Mac (`start-dev.sh`):**
```bash
#!/bin/bash
cd "$(dirname "$0")"

# Iniciar mock en background
./scripts/start-mock.sh &
MOCK_PID=$!

sleep 3

# Leer endpoint del archivo
export SOAP_ENDPOINT=$(grep "endpoint=" /tmp/file-processor-mock.info | cut -d= -f2)
echo "Usando endpoint: $SOAP_ENDPOINT"

# Iniciar microservicio
./gradlew bootRun --args='--spring.profiles.active=dev'

# Al terminar, detener mock
kill $MOCK_PID 2>/dev/null
```

### Opción B: Configuración estática

Si quieres un puerto fijo (ej: 9000), modifica `application-windows.yml`:

```yaml
app:
  soap:
    endpoint: http://localhost:9000/soap/fileservice
```

Y ejecuta mock con puerto específico:

```bash
# Compilar primero
javac -d build/classes/java/test src/test/java/com/example/fileprocessor/mock/PortableSoapMock.java

# Ejecutar con puerto fijo
java -cp build/classes/java/test com.example.fileprocessor.mock.PortableSoapMock 9000
```

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

### "Puerto no disponible"

El script busca automáticamente en el rango 9000-9999. Si todos están ocupados:

```bash
# Ver puertos usados
# Windows:
netstat -ano | findstr "9000 9001 9002"

# Linux/Mac:
netstat -tuln | grep "900[0-9]"
```

### Mock no responde

1. Verifica que está corriendo:
   ```bash
   # Windows:
   tasklist | findstr "java"
   
   # Linux/Mac:
   pgrep -f "PortableSoapMock"
   ```

2. Prueba el endpoint directamente:
   ```bash
   curl http://localhost:PORT/soap/fileservice -X POST -H "Content-Type: text/xml" -d "<test>"
   ```

3. Detén y reinicia:
   ```bash
   ./scripts/stop-mock.sh  # o stop-mock.bat
   ./scripts/start-mock.sh  # o start-mock.bat
   ```
