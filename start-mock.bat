@echo off
REM Script para iniciar el Mock SOAP Server en Windows
chcp 65001 >nul
setlocal enabledelayedexpansion

echo Iniciando Mock SOAP Server...
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
    if exist "C:\Program Files (x86)\Java" (
        for /d %%i in ("C:\Program Files (x86)\Java\*") do (
            set JAVA_HOME=%%i
            goto :javaFound
        )
    )
)

:javaFound
if not defined JAVA_HOME (
    echo ERROR: No se encontro JAVA_HOME ni Java instalado.
    echo Por favor instala Java 21 o define JAVA_HOME.
    pause
    exit /b 1
)

echo Java encontrado en: %JAVA_HOME%
"%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr "version"
echo.

REM Verificar puerto 8081
echo Verificando puerto 8081...
netstat -ano | findstr ":8081" >nul
if !errorlevel! equ 0 (
    echo ADVERTENCIA: El puerto 8081 esta ocupado.
    echo Intentando liberar...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8081"') do (
        taskkill /F /PID %%a >nul 2>&1
        echo Proceso %%a detenido.
    )
    timeout /t 2 /nobreak >nul
)

echo.
echo Compilando...

REM Crear directorio si no existe
if not exist "build\classes\java\test" mkdir "build\classes\java\test"

REM Compilar con javac
"%JAVA_HOME%\bin\javac.exe" -d "build\classes\java\test" "src\test\java\com\example\fileprocessor\mock\SimpleSoapMock.java" 2>nul
if !errorlevel! neq 0 (
    echo Compilando con Gradle...
    call gradlew.bat testClasses --quiet
    if !errorlevel! neq 0 (
        echo ERROR: No se pudo compilar el proyecto.
        pause
        exit /b 1
    )
)

echo Iniciando servidor...
echo.

REM Ejecutar en segundo plano usando start /B
start /B "" "%JAVA_HOME%\bin\java.exe" -cp "build\classes\java\test" com.example.fileprocessor.mock.SimpleSoapMock >nul 2>&1

REM Guardar PID (aproximado para Windows)
for /f "tokens=2" %%a in ('tasklist ^| findstr "java.exe" ^| findstr /I "SimpleSoapMock"') do (
    echo %%a > .mock.pid
    set MOCK_PID=%%a
)

REM Esperar a que inicie
timeout /t 3 /nobreak >nul

REM Verificar que esta corriendo
netstat -ano | findstr ":8081" >nul
if !errorlevel! equ 0 (
    echo ========================================
    echo Mock iniciado correctamente!
    for /f %%a in (.mock.pid) do echo PID: %%a
    echo Endpoint: http://localhost:8081/soap/fileservice
    echo ========================================
    echo.
    echo Para detenerlo: stop-mock.bat
    echo.
) else (
    echo ERROR: El mock no pudo iniciar correctamente.
    del .mock.pid 2>nul
    pause
    exit /b 1
)
