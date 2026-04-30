#!/bin/bash

# Script portable para iniciar Mock REST de Productos en Unix/Linux/macOS
# Busca automaticamente un puerto disponible (default 3001)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
MOCK_CLASS="com.example.fileprocessor.mock.ProductRestMock"

# Colores para output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "========================================"
echo "  Product REST Mock Server - Portable"
echo "========================================"
echo ""

# Detectar Java
detect_java() {
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        JAVA_CMD="$JAVA_HOME/bin/java"
        return 0
    fi

    if command -v java &> /dev/null; then
        JAVA_CMD="java"
        return 0
    fi

    # Buscar en ubicaciones comunes
    COMMON_PATHS=(
        "/opt/java/*/Contents/Home"
        "/Library/Java/JavaVirtualMachines/*/Contents/Home"
        "/usr/lib/jvm/*/"
        "/usr/local/opt/openjdk*"
        "/usr/java/*/"
    )

    for path_pattern in "${COMMON_PATHS[@]}"; do
        for path in $path_pattern; do
            if [ -d "$path" ] && [ -x "$path/bin/java" ]; then
                export JAVA_HOME="$path"
                JAVA_CMD="$path/bin/java"
                return 0
            fi
        done
    done

    return 1
}

echo "Buscando Java..."
if detect_java; then
    echo -e "${GREEN}✓${NC} Java encontrado: $JAVA_CMD"
    "$JAVA_CMD" -version 2>&1 | head -1
else
    echo -e "${RED}✗${NC} ERROR: No se encontro Java instalado"
    echo "Por favor instala Java 21+ o define JAVA_HOME"
    exit 1
fi
echo ""

# Compilar clase
echo "Compilando mock..."
BUILD_DIR="$PROJECT_DIR/build/classes/java/test"
mkdir -p "$BUILD_DIR"

if [ ! -f "$BUILD_DIR/com/example/fileprocessor/mock/ProductRestMock.class" ]; then
    if [ -f "$PROJECT_DIR/gradlew" ]; then
        echo "Compilando con Gradle..."
        cd "$PROJECT_DIR"
        ./gradlew testClasses --quiet 2>/dev/null || {
            echo -e "${RED}✗${NC} Error compilando con Gradle"
            exit 1
        }
    else
        echo "Compilando con javac..."
        "$JAVA_HOME/bin/javac" -d "$BUILD_DIR" \
            "$PROJECT_DIR/src/test/java/com/example/fileprocessor/mock/ProductRestMock.java" 2>/dev/null || {
            echo -e "${RED}✗${NC} Error compilando. Intenta ejecutar: ./gradlew testClasses"
            exit 1
        }
    fi
fi

echo -e "${GREEN}✓${NC} Compilacion exitosa"
echo ""

# Iniciar servidor
echo "Iniciando servidor..."
cd "$PROJECT_DIR"

# Iniciar el mock (busca puerto automaticamente si no se especifica)
exec "$JAVA_CMD" -cp "$BUILD_DIR" "$MOCK_CLASS" "$@"
