@echo off
REM Script para detener el Mock SOAP Server en Windows

echo Deteniendo Mock SOAP Server...

REM Metodo 1: Buscar por el nombre de la clase y matar
for /f "tokens=2" %%a in ('tasklist ^| findstr "java.exe" ^| findstr "SimpleSoapMock"') do (
    taskkill /PID %%a /F >nul 2>&1
    echo Mock detenido (PID: %%a)
)

REM Metodo 2: Buscar por el puerto 8081
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8081"') do (
    taskkill /PID %%a /F >nul 2>&1
    echo Mock detenido (puerto 8081, PID: %%a)
)

REM Metodo 3: Si hay un PID file
if exist .mock.pid (
    set /p PID=<.mock.pid
    taskkill /PID %PID% /F >nul 2>&1
    echo Mock detenido (PID file: %PID%)
    del .mock.pid
)

echo Hecho.
pause
