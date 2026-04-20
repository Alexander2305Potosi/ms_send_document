#!/bin/bash

# Script para detener el Mock SOAP Server
# Funciona en Linux y macOS

echo "Deteniendo Mock SOAP Server..."

# Metodo 1: Buscar por el nombre de la clase y matar con -9
if pgrep -f "SimpleSoapMock" > /dev/null 2>&1; then
    pkill -9 -f "SimpleSoapMock" 2>/dev/null
    sleep 1
    echo "Mock detenido (SimpleSoapMock)"
fi

# Metodo 2: Buscar por el puerto 8081 y matar
PID=$(lsof -ti:8081 2>/dev/null)
if [ -n "$PID" ]; then
    kill -9 $PID 2>/dev/null
    sleep 1
    echo "Mock detenido (puerto 8081, PID: $PID)"
fi

# Metodo 3: Si hay un PID file guardado
if [ -f ".mock.pid" ]; then
    PID=$(cat .mock.pid)
    if kill -0 $PID 2>/dev/null; then
        kill -9 $PID 2>/dev/null
        sleep 1
        echo "Mock detenido (PID file: $PID)"
    fi
    rm -f .mock.pid
fi

# Verificar que el puerto realmente se liberó
echo "Verificando que el puerto 8081 esté libre..."
for i in {1..5}; do
    PID=$(lsof -ti:8081 2>/dev/null)
    if [ -z "$PID" ]; then
        echo "Puerto 8081 liberado correctamente."
        exit 0
    fi
    echo "  Esperando... (intento $i/5)"
    sleep 1
done

# Si aún hay un proceso, mostrar advertencia
PID=$(lsof -ti:8081 2>/dev/null)
if [ -n "$PID" ]; then
    echo "ADVERTENCIA: El puerto 8081 sigue ocupado por PID: $PID"
    echo "Proceso: $(ps -p $PID -o comm= 2>/dev/null)"
    echo "Intenta manualmente: kill -9 $PID"
    exit 1
else
    echo "Puerto 8081 liberado."
fi
