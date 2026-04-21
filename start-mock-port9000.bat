@echo off
chcp 65001 >nul
echo ========================================
echo  Mock SOAP - Puerto 9000 (No Admin)
echo ========================================
echo.

REM Buscar Java
if not defined JAVA_HOME (
    if exist "C:\Program Files\Microsoft\OpenJDK" (
        for /d %%i in ("C:\Program Files\Microsoft\OpenJDK\*") do (
            set JAVA_HOME=%%i
            goto :javaFound
        )
    )
)

:javaFound
if not defined JAVA_HOME (
    echo ERROR: No se encontro Java
    pause
    exit /b 1
)

echo Usando Java de: %JAVA_HOME%
echo.

REM Compilar el mock
echo Compilando mock en puerto 9000...
"%JAVA_HOME%\bin\javac.exe" SimpleSoapMockPort9000.java 2>nul

if %errorlevel% neq 0 (
    echo ERROR: No se pudo compilar. Verifica que tienes el archivo SimpleSoapMockPort9000.java
    pause
    exit /b 1
)

echo.
echo Iniciando servidor en puerto 9000...
echo.
echo ========================================
echo IMPORTANTE: Configura la variable de entorno:
echo   SOAP_ENDPOINT=http://localhost:9000/soap/fileservice
echo ========================================
echo.

java SimpleSoapMockPort9000
