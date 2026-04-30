@echo off
REM Script portable para iniciar Mock REST de Documentos en Windows
REM El mock busca automaticamente un puerto disponible (default 8081)

chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo   Document REST Mock Server - Portable
echo ========================================
echo.

REM Guardar directorio del script
set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."
cd /d "%PROJECT_DIR%"

set "MOCK_CLASS=com.example.fileprocessor.mock.DocumentRestMock"
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
    for /d %%K in (%%~J) do (
        if exist "%%~K\bin\java.exe" (
            set "JAVA_HOME=%%~K"
            set "JAVA_CMD=%%~K\bin\java.exe"
            goto :javaFound
        )
    )
)

REM Verificar si java esta en PATH
where java >nul 2>&1
if !errorlevel! equ 0 (
    set "JAVA_CMD=java"
    goto :javaFound
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
REM Paso 2: Compilar si es necesario
REM ========================================
echo Verificando clases del mock...

if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"

REM Verificar si ya esta compilado
if exist "%BUILD_DIR%\com\example\fileprocessor\mock\DocumentRestMock.class" (
    echo [OK] Clases ya compiladas
    goto :ready
)

REM Compilar con Gradle
echo Compilando con Gradle...
call gradlew.bat testClasses --quiet
if !errorlevel! neq 0 (
    echo ERROR: Fallo la compilacion con Gradle.
    echo Intenta: .\gradlew testClasses
    pause
    exit /b 1
)

echo [OK] Compilacion exitosa
echo.

:ready
REM ========================================
REM Paso 3: Iniciar servidor
REM ========================================
echo Iniciando servidor...
echo.

REM Los argumentos pasados al script se reenvian al mock
REM El mock(auto-detecta puerto disponible si no se especifica)
"!JAVA_CMD!" -cp "%BUILD_DIR%" %MOCK_CLASS% %*

REM Si llegamos aqui, el servidor se detuvo
exit /b 0
