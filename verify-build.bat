@echo off
REM Script de verificación de build para Windows

echo ========================================
echo   Verificación de Build
echo ========================================
echo.

REM Verificar Java
java -version >nul 2>&1
if errorlevel 1 (
    echo [✗] Java no encontrado
    exit /b 1
)

echo [✓] Java encontrado:
java -version 2>&1 | findstr "version"
echo.

REM Verificar Gradle wrapper
if not exist "gradlew.bat" (
    echo [✗] Gradle wrapper no encontrado
    exit /b 1
)

echo [✓] Gradle wrapper encontrado
echo.

REM Limpiar build anterior
echo Limpiando build anterior...
call gradlew.bat clean --quiet
if errorlevel 1 (
    echo [✗] Error limpiando build
    exit /b 1
)
echo [✓] Build limpio
echo.

REM Compilar
echo Compilando proyecto...
call gradlew.bat compileJava --no-daemon
if errorlevel 1 (
    echo [✗] Errores de compilación
    exit /b 1
)
echo [✓] Compilación exitosa
echo.

REM Compilar tests
echo Compilando tests...
call gradlew.bat compileTestJava --no-daemon
if errorlevel 1 (
    echo [✗] Errores compilando tests
    exit /b 1
)
echo [✓] Tests compilados exitosamente
echo.

REM Ejecutar tests
echo Ejecutando tests...
call gradlew.bat test --no-daemon
if errorlevel 1 (
    echo [✗] Algunos tests fallaron
    exit /b 1
)
echo [✓] Todos los tests pasaron
echo.

echo ========================================
echo [✓] Build exitoso!
echo ========================================
echo.
echo Para iniciar el servicio:
echo   start-dev.bat
pause
