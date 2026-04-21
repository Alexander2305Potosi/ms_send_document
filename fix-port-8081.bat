@echo off
chcp 65001 >nul
echo ========================================
echo  Liberador de Puerto 8081 - Windows
echo ========================================
echo.

echo Paso 1: Matando todos los procesos Java...
taskkill /F /IM java.exe 2>nul
taskkill /F /IM javaw.exe 2>nul
timeout /t 3 /nobreak >nul

echo.
echo Paso 2: Buscando procesos en puerto 8081...
netstat -ano | findstr ":8081"

echo.
echo Paso 3: Intentando liberar puerto 8081...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8081"') do (
    echo   - Matando proceso PID: %%a
    taskkill /F /PID %%a >nul 2>&1
    taskkill /F /PID %%a >nul 2>&1
)

timeout /t 3 /nobreak >nul

echo.
echo Paso 4: Verificando que puerto este libre...
netstat -ano | findstr ":8081" >nul
if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo  ERROR: Puerto 8081 sigue ocupado
    echo ========================================
    echo.
    echo Procesos encontrados:
    netstat -ano | findstr ":8081"
    echo.
    echo Intenta estas opciones:
    echo.
    echo Opcion 1 - Reiniciar la computadora
    echo Opcion 2 - Abrir Task Manager (Ctrl+Shift+Esc)
    echo            Buscar "OpenJDK Platform binary" o "Java"
    echo            Clic derecho - Finalizar tarea
    echo Opcion 3 - Ejecutar como Administrador:
    echo            netstat -ano ^| findstr :8081
    echo            taskkill /F /PID ^<PID^>
    echo.
    pause
    exit /b 1
) else (
    echo.
    echo ========================================
    echo  OK: Puerto 8081 liberado!
    echo ========================================
    echo.
    echo Ahora puedes ejecutar start-mock-fixed.bat
    echo.
    pause
)
