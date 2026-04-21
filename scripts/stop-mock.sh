#!/bin/bash

# Script portable para detener Mock SOAP
# Funciona en Unix/Linux/macOS

set -e

# Colores
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo "========================================"
echo "  Detener Mock SOAP Server"
echo "========================================"
echo ""

# Buscar proceso del mock
found=false

# Buscar por nombre de clase
if command -v pgrep &> /dev/null; then
    pids=$(pgrep -f "PortableSoapMock" 2>/dev/null || true)
    if [ -n "$pids" ]; then
        echo "Procesos encontrados:"
        echo "$pids" | while read pid; do
            echo "  - PID: $pid"
            kill -9 "$pid" 2>/dev/null || true
        done
        found=true
        sleep 2
    fi
fi

# Buscar por puerto (9000 o rango alternativo)
if command -v lsof &> /dev/null; then
    for port in 9000 9001 9002 9003 9004 9005; do
        pid=$(lsof -ti:$port 2>/dev/null)
        if [ -n "$pid" ]; then
            # Verificar si es Java
            cmd=$(ps -p $pid -o comm= 2>/dev/null || echo "")
            if echo "$cmd" | grep -qi "java"; then
                echo "Deteniendo proceso Java en puerto $port (PID: $pid)..."
                kill -9 "$pid" 2>/dev/null || true
                found=true
            fi
        fi
    done
fi

# Limpiar archivo de info
rm -f "/tmp/file-processor-mock.info" 2>/dev/null || true

if [ "$found" = true ]; then
    sleep 1
    echo -e "${GREEN}✓${NC} Mock detenido"
else
    echo -e "${YELLOW}⚠${NC} No se encontraron procesos del mock"
fi

echo ""
