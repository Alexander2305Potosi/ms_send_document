@echo off
REM Script portable para iniciar Mock SOAP en Windows
REM Busca automaticamente un puerto disponible

chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo   Mock SOAP Server - Portable
echo ========================================
echo.

REM Guardar directorio del script
set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."
cd /d "%PROJECT_DIR%"

set "MOCK_CLASS=com.example.fileprocessor.mock.PortableSoapMock"
set "BUILD_DIR=%PROJECT_DIR%\build\classes\java\test"

REM ========================================
REM Paso 1: Detectar Java
REM ========================================
echo Buscando Java...

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
        goto :javaFound
    )
)

REM Buscar en ubicaciones comunes de Windows
for %%J in (
    "C:\Program Files\Microsoft\OpenJDK\*"
    "C:\Program Files\Java\*"
    "C:\Program Files (x86)\Java\*"
    "C:\Program Files\Eclipse Adoptium\*"
    "C:\Program Files\Amazon Corretto\*"
) do (
    if exist "%%~J\bin\java.exe" (
        set "JAVA_HOME=%%~J"
        set "JAVA_CMD=%%~J\bin\java.exe"
        goto :javaFound
    )
)

REM Verificar si java esta en PATH
where java >nul 2>&1
if !errorlevel! equ 0 (
    set "JAVA_CMD=java"
    for /f "tokens=*" %%i in ('java -version 2>&1') do (
        echo %%i | findstr "version" >nul && goto :javaFound
    )
)

echo ERROR: No se encontro Java instalado.
echo Por favor instala Java 21+ o define JAVA_HOME.
pause
exit /b 1

:javaFound
echo [OK] Java encontrado: !JAVA_CMD!
"!JAVA_CMD!" -version 2>&1 | findstr "version"
echo.

REM ========================================
REM Paso 2: Verificar/Liberar puertos
REM ========================================
echo Verificando disponibilidad de puertos...

REM Funcion para verificar si puerto esta libre
call :checkPort 9000
if !errorlevel! neq 0 (
    echo [ADVERTENCIA] Puerto 9000 ocupado
    echo Intentando liberar...
    call :killPort 9000

    REM Verificar nuevamente
    call :checkPort 9000
    if !errorlevel! neq 0 (
        echo [INFO] Puerto 9000 no se puede liberar, se buscara alternativo
    )
)
echo.

REM ========================================
REM Paso 3: Compilar
REM ========================================
echo Compilando mock...

if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"

REM Verificar si ya esta compilado
if exist "%BUILD_DIR%\com\example\fileprocessor\mock\PortableSoapMock.class" (
    echo [OK] Clases ya compiladas
    goto :compiled
)

REM Intentar compilar con javac
"!JAVA_CMD:\java.exe=\javac.exe!" -d "%BUILD_DIR%" "!PROJECT_DIR!\src\test\java\com\example\fileprocessor\mock\PortableSoapMock.java" >nul 2>&1
if !errorlevel! equ 0 (
    echo [OK] Compilado con javac
    goto :compiled
)

REM Intentar con Gradle
if exist "gradlew.bat" (
    echo Compilando con Gradle...
    call gradlew.bat testClasses --quiet
    if !errorlevel! equ 0 (
        echo [OK] Compilado con Gradle
        goto :compiled
    )
)

echo ERROR: No se pudo compilar el mock.
pause
exit /b 1

:compiled
echo.

REM ========================================
REM Paso 4: Iniciar servidor
REM ========================================
echo Iniciando servidor...
echo.

REM Iniciar el servidor (el Java buscara puerto automaticamente)
REM Argumentos extra (escenarios) se pasan directamente
"!JAVA_CMD!" -cp "%BUILD_DIR%" %MOCK_CLASS% %*

REM Si llegamos aqui, el servidor se detuvo
exit /b 0

REM ========================================
REM Funciones
REM ========================================

:checkPort
REM Verifica si un puerto esta libre
REM Retorna 0 si libre, 1 si ocupado
netstat -ano | findstr ":%~1" | findstr "LISTENING" >nul
if !errorlevel! equ 0 (
    exit /b 1
) else (
    exit /b 0
)

:killPort
REM Mata procesos en un puerto
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%~1" ^| findstr "LISTENING"') do (
    echo   Matando proceso %%a en puerto %~1...
    taskkill /F /PID %%a >nul 2>&1
)
timeout /t 2 /nobreak >nul
exit /b 0
