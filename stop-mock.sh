#!/bin/bash

# Script para detener el Mock SOAP Server
# Funciona en Linux y macOS

echo "Deteniendo Mock SOAP Server..."

# Metodo 1: Buscar por el nombre de la clase y matar
if pgrep -f "SimpleSoapMock" > /dev/null 2>&1; then
    pkill -f "SimpleSoapMock"
    echo "Mock detenido (SimpleSoapMock)"
fi

# Metodo 2: Buscar por el puerto 8081 y matar
PID=$(lsof -ti:8081 2>/dev/null)
if [ -n "$PID" ]; then
    kill -9 $PID 2>/dev/null
    echo "Mock detenido (puerto 8081, PID: $PID)"
fi

# Metodo 3: Si hay un PID file guardado
if [ -f ".mock.pid" ]; then
    PID=$(cat .mock.pid)
    if kill -0 $PID 2>/dev/null; then
        kill -9 $PID 2>/dev/null
        echo "Mock detenido (PID file: $PID)"
    fi
    rm -f .mock.pid
fi

echo "Hecho."
