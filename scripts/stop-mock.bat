@echo off
REM Script portable para detener Mock SOAP en Windows

chcp 65001 >nul
echo ========================================
echo   Detener Mock SOAP Server
echo ========================================
echo.

set "found=false"

REM Buscar procesos Java con PortableSoapMock
echo Buscando procesos del mock...
for /f "tokens=2" %%a in ('tasklist ^| findstr "java.exe"') do (
    REM Verificar si es el mock buscando en la linea de comandos
    wmic process where "ProcessId=%%a" get CommandLine 2>nul | findstr "PortableSoapMock" >nul
    if !errorlevel! equ 0 (
        echo Deteniendo PID: %%a
e        taskkill /F /PID %%a >nul 2>&1
        set "found=true"
    )
)

REM Buscar en puertos comunes
for %%P in (8081 9000 9001 9002 9003 9004 9005) do (
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%%P" ^| findstr "LISTENING"') do (
        echo Deteniendo proceso en puerto %%P (PID: %%a)
        taskkill /F /PID %%a >nul 2>&1
        set "found=true"
    )
)

REM Limpiar archivo temporal
del /q "%TEMP%\file-processor-mock.info" 2>nul

if "%found%"=="true" (
    echo.
    echo [OK] Mock detenido
) else (
    echo.
    echo [INFO] No se encontraron procesos del mock
)

echo.
pause
