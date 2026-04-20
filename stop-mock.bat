@echo off
REM Script para detener el Mock SOAP Server en Windows
setlocal enabledelayedexpansion

echo Deteniendo Mock SOAP Server...
echo.

set FOUND=0

REM Metodo 1: Buscar por el nombre de la clase
for /f "tokens=2" %%a in ('tasklist ^| findstr "java.exe" ^| findstr /I "SimpleSoapMock"') do (
    taskkill /F /PID %%a >nul 2>&1
    if !errorlevel! equ 0 (
        echo Mock detenido (PID: %%a)
        set FOUND=1
    )
)

REM Metodo 2: Buscar por el puerto 8081
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8081"') do (
    taskkill /F /PID %%a >nul 2>&1
    if !errorlevel! equ 0 (
        echo Mock detenido (puerto 8081, PID: %%a)
        set FOUND=1
    )
)

REM Metodo 3: Si hay un PID file
if exist .mock.pid (
    set /p PID=<.mock.pid
    taskkill /F /PID !PID! >nul 2>&1
    if !errorlevel! equ 0 (
        echo Mock detenido (PID file: !PID!)
        set FOUND=1
    )
    del .mock.pid 2>nul
)

REM Esperar un poco
timeout /t 2 /nobreak >nul

REM Verificar que el puerto se liberó
echo.
echo Verificando que el puerto 8081 este libre...
set PORT_IN_USE=0
for /f %%a in ('netstat -ano ^| findstr ":8081" ^| find /c /v ""') do (
    if %%a gtr 0 set PORT_IN_USE=1
)

if !PORT_IN_USE! equ 1 (
    echo ADVERTENCIA: El puerto 8081 sigue ocupado.
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8081"') do (
        echo PID que usa el puerto: %%a
        echo Intenta manualmente: taskkill /F /PID %%a
    )
    pause
    exit /b 1
) else (
    echo Puerto 8081 liberado correctamente.
)

if !FOUND! equ 0 (
    echo No se encontro ningun proceso de mock corriendo.
)

echo.
pause
