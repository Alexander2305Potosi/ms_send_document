#!/bin/bash

# Script de verificación de build
# Ejecuta: ./verify-build.sh

set -e

echo "========================================"
echo "  Verificación de Build"
echo "========================================"
echo ""

# Colores
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

# Verificar Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}✗${NC} Java no encontrado"
    exit 1
fi

echo -e "${GREEN}✓${NC} Java encontrado:"
java -version 2>&1 | head -1
echo ""

# Verificar Gradle wrapper
if [ ! -f "./gradlew" ]; then
    echo -e "${RED}✗${NC} Gradle wrapper no encontrado"
    exit 1
fi

echo -e "${GREEN}✓${NC} Gradle wrapper encontrado"
echo ""

# Limpiar build anterior
echo "Limpiando build anterior..."
./gradlew clean --quiet
echo -e "${GREEN}✓${NC} Build limpio"
echo ""

# Compilar
echo "Compilando proyecto..."
./gradlew compileJava --no-daemon
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} Compilación exitosa"
else
    echo -e "${RED}✗${NC} Errores de compilación"
    exit 1
fi
echo ""

# Compilar tests
echo "Compilando tests..."
./gradlew compileTestJava --no-daemon
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} Tests compilados exitosamente"
else
    echo -e "${RED}✗${NC} Errores compilando tests"
    exit 1
fi
echo ""

# Ejecutar tests
echo "Ejecutando tests..."
./gradlew test --no-daemon
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} Todos los tests pasaron"
else
    echo -e "${RED}✗${NC} Algunos tests fallaron"
    exit 1
fi
echo ""

echo "========================================"
echo -e "${GREEN}✓${NC} Build exitoso!"
echo "========================================"
echo ""
echo "Para iniciar el servicio:"
echo "  ./start-dev.sh"
