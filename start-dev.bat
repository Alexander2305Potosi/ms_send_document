@echo off
REM Script completo para desarrollo en Windows
REM Inicia Mock + Microservicio automaticamente

chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo   File Processor - Modo Desarrollo
echo ========================================
echo.

set "PROJECT_DIR=%~dp0"
cd /d "%PROJECT_DIR%"

REM ========================================
REM Paso 1: Detener procesos previos
REM ========================================
echo Paso 1: Limpiando procesos previos...
call scripts\stop-mock.bat >nul 2>&1
timeout /t 2 /nobreak >nul
echo [OK]
echo.

REM ========================================
REM Paso 2: Iniciar Mock SOAP
REM ========================================
echo Paso 2: Iniciando Mock SOAP...

REM Verificar si existe el mock portable
if not exist "src\test\java\com\example\fileprocessor\mock\PortableSoapMock.java" (
    echo ERROR: No se encuentra PortableSoapMock.java
    echo Asegurate de estar en el directorio raiz del proyecto.
    pause
    exit /b 1
)

REM Detectar Java
if not defined JAVA_HOME (
    for %%J in (
        "C:\Program Files\Microsoft\OpenJDK\*"
        "C:\Program Files\Java\*"
    ) do (
        if exist "%%~J\bin\java.exe" (
            set "JAVA_HOME=%%~J"
            goto :javaFound
        )
    )
)

:javaFound
if not defined JAVA_HOME (
    echo ERROR: Java no encontrado. Instala Java 21 o define JAVA_HOME.
    pause
    exit /b 1
)

set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
echo   Java: %JAVA_HOME%

REM Compilar mock si es necesario
if not exist "build\classes\java\test\com\example\fileprocessor\mock\PortableSoapMock.class" (
    echo   Compilando mock...
    call gradlew.bat testClasses --quiet >nul 2>&1
)

REM Iniciar mock en background
start "Mock SOAP" /B "%JAVA_CMD%" -cp "build\classes\java\test" com.example.fileprocessor.mock.PortableSoapMock

echo   Esperando que inicie (3 segundos)...
timeout /t 3 /nobreak >nul

REM Verificar que el mock guardo su info
if not exist "%TEMP%\file-processor-mock.info" (
    echo [ADVERTENCIA] No se pudo verificar el mock, usando puerto por defecto...
    set "SOAP_ENDPOINT=http://localhost:9000/soap/fileservice"
) else (
    REM Leer endpoint del archivo
    for /f "tokens=2 delims==" %%a in ('type %TEMP%\file-processor-mock.info ^| findstr "endpoint"') do (
        set "SOAP_ENDPOINT=%%a"
    )
)

echo   Mock listo en: %SOAP_ENDPOINT%
echo [OK]
echo.

REM ========================================
REM Paso 3: Iniciar Microservicio
REM ========================================
echo Paso 3: Iniciando Microservicio...
echo   SOAP_ENDPOINT=%SOAP_ENDPOINT%
echo.
echo Presiona Ctrl+C para detener todo
echo ========================================
echo.

REM Iniciar microservicio con la variable configurada
set "SOAP_ENDPOINT=%SOAP_ENDPOINT%"
call gradlew.bat bootRun --args="--spring.profiles.active=dev"

REM ========================================
REM Cleanup al salir
REM ========================================
echo.
echo Deteniendo mock...
taskkill /FI "WINDOWTITLE eq Mock SOAP*" >nul 2>&1
call scripts\stop-mock.bat >nul 2>&1

echo.
echo [OK] Desarrollo finalizado.
pause
