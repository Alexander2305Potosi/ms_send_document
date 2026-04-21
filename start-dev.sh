#!/bin/bash

# Script completo para desarrollo en Linux/Mac
# Inicia Mock + Microservicio automaticamente

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================"
echo "  File Processor - Modo Desarrollo"
echo "========================================"
echo ""

# ========================================
# Paso 1: Detener procesos previos
# ========================================
echo "Paso 1: Limpiando procesos previos..."
./scripts/stop-mock.sh >/dev/null 2>&1 || true
sleep 2
echo -e "${GREEN}✓${NC} OK"
echo ""

# ========================================
# Paso 2: Iniciar Mock SOAP
# ========================================
echo "Paso 2: Iniciando Mock SOAP..."

# Verificar que existe el mock
if [ ! -f "src/test/java/com/example/fileprocessor/mock/PortableSoapMock.java" ]; then
    echo "ERROR: No se encuentra PortableSoapMock.java"
    exit 1
fi

# Detectar Java
if ! command -v java &> /dev/null; then
    echo "ERROR: Java no encontrado"
    exit 1
fi

# Compilar si es necesario
if [ ! -f "build/classes/java/test/com/example/fileprocessor/mock/PortableSoapMock.class" ]; then
    echo "  Compilando mock..."
    ./gradlew testClasses --quiet
fi

# Iniciar mock en background
echo "  Iniciando servidor..."
java -cp "build/classes/java/test" com.example.fileprocessor.mock.PortableSoapMock &
MOCK_PID=$!

# Esperar a que inicie y guarde su info
sleep 3

# Leer endpoint del archivo temporal
INFO_FILE="/tmp/file-processor-mock.info"
if [ -f "$INFO_FILE" ]; then
    export SOAP_ENDPOINT=$(grep "endpoint=" "$INFO_FILE" | cut -d= -f2)
else
    echo -e "${YELLOW}⚠${NC} No se pudo detectar endpoint, usando default"
    export SOAP_ENDPOINT="http://localhost:9000/soap/fileservice"
fi

echo "  Mock listo en: $SOAP_ENDPOINT"
echo -e "${GREEN}✓${NC} OK"
echo ""

# ========================================
# Paso 3: Iniciar Microservicio
# ========================================
echo "Paso 3: Iniciando Microservicio..."
echo "  SOAP_ENDPOINT=$SOAP_ENDPOINT"
echo ""
echo "Presiona Ctrl+C para detener todo"
echo "========================================"
echo ""

# Capturar Ctrl+C para limpieza
cleanup() {
    echo ""
    echo "Deteniendo servicios..."
    kill $MOCK_PID 2>/dev/null || true
    ./scripts/stop-mock.sh >/dev/null 2>&1 || true
    echo -e "${GREEN}✓${NC} Desarrollo finalizado."
    exit 0
}
trap cleanup SIGINT SIGTERM

# Iniciar microservicio
./gradlew bootRun --args="--spring.profiles.active=dev" || true

# Cleanup al salir
cleanup
