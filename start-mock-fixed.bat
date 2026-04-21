@echo off
REM Script corregido para iniciar el Mock SOAP Server en Windows
chcp 65001 >nul
setlocal enabledelayedexpansion

echo Iniciando Mock SOAP Server...
echo.

REM Guardar directorio actual y moverse al directorio del script
cd /d "%~dp0"
echo Directorio del proyecto: %CD%
echo.

REM Buscar Java en ubicaciones comunes
if not defined JAVA_HOME (
    if exist "C:\Program Files\Microsoft\OpenJDK" (
        for /d %%i in ("C:\Program Files\Microsoft\OpenJDK\*") do (
            set JAVA_HOME=%%i
            goto :javaFound
        )
    )
    if exist "C:\Program Files\Java" (
        for /d %%i in ("C:\Program Files\Java\*") do (
            set JAVA_HOME=%%i
            goto :javaFound
        )
    )
)

:javaFound
if not defined JAVA_HOME (
    echo ERROR: No se encontro JAVA_HOME ni Java instalado.
    pause
    exit /b 1
)

echo Java encontrado en: %JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr "version"
echo.

REM Verificar puerto 8081 y liberarlo si es necesario
echo Verificando puerto 8081...
netstat -ano | findstr ":8081" >nul
if !errorlevel! equ 0 (
    echo ADVERTENCIA: El puerto 8081 esta ocupado. Liberando...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8081"') do (
        echo Matando proceso %%a...
        taskkill /F /PID %%a >nul 2>&1
    )
    timeout /t 2 /nobreak >nul
)

echo.
echo Compilando clases del mock...

REM Crear directorio si no existe
if not exist "build\classes\java\test" mkdir "build\classes\java\test"

REM Intentar compilar con javac directamente
"%JAVA_HOME%\bin\javac.exe" -d "build\classes\java\test" "src\test\java\com\example\fileprocessor\mock\SimpleSoapMock.java" 2>nul

if !errorlevel! neq 0 (
    echo javac fallo, intentando con Gradle...
    if exist "gradlew.bat" (
        call gradlew.bat testClasses --quiet
    ) else if exist "..\gradlew.bat" (
        call ..\gradlew.bat testClasses --quiet
    ) else (
        echo ERROR: No se encuentra gradlew.bat ni javac pudo compilar.
        echo Asegurate de estar en el directorio del proyecto.
        pause
        exit /b 1
    )
)

echo.
echo Iniciando servidor mock...

REM Ejecutar de forma que podamos ver errores
start "Mock SOAP Server" /B "%JAVA_HOME%\bin\java.exe" -cp "build\classes\java\test" com.example.fileprocessor.mock.SimpleSoapMock

echo.
echo Esperando 3 segundos para que inicie...
timeout /t 3 /nobreak >nul

echo.
echo Verificando que el servidor este activo...

REM Intentar conectar para verificar que esta funcionando
curl -s -o nul -w "%%{http_code}" http://localhost:8081/soap/fileservice > .mock-status.txt 2>nul
set /p STATUS=<.mock-status.txt 2>nul
del .mock-status.txt 2>nul

if "!STATUS!"=="200" (
    echo ========================================
    echo Mock iniciado correctamente!
    echo Endpoint: http://localhost:8081/soap/fileservice
    echo ========================================
    echo.
    echo El servidor esta corriendo en la ventana.
    echo Cierra la ventana para detenerlo.
) else (
    echo ERROR: El mock no responde correctamente.
    echo Codigo HTTP: !STATUS!
    echo.
    echo Posibles causas:
    echo - Firewall de Windows bloqueando el puerto 8081
    echo - Otra aplicacion usando el puerto
    echo - Error en la compilacion
    echo.
    echo Intenta compilar manualmente:
    echo   gradlew testClasses
)

pause
