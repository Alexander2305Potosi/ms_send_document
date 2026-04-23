#!/bin/bash

# Script portable para iniciar Mock REST de Documentos en Unix/Linux/macOS
# Busca automáticamente un puerto disponible (default 8081)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
MOCK_CLASS="com.example.fileprocessor.mock.DocumentRestMock"

# Colores para output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "========================================"
echo "  Document REST Mock Server - Portable"
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

# Función para verificar si un puerto está libre
check_port() {
    local port=$1
    if command -v lsof &> /dev/null; then
        ! lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1
    elif command -v netstat &> /dev/null; then
        ! netstat -tuln 2>/dev/null | grep -q ":$port "
    elif command -v ss &> /dev/null; then
        ! ss -tuln 2>/dev/null | grep -q ":$port "
    else
        # Intentar con bash built-in
        ! (exec 2>/dev/null; echo > /dev/tcp/127.0.0.1/$port)
    fi
}

# Función para matar proceso en un puerto
kill_port() {
    local port=$1
    if command -v lsof &> /dev/null; then
        local pid=$(lsof -ti:$port 2>/dev/null)
        if [ -n "$pid" ]; then
            echo "Liberando puerto $port (PID: $pid)..."
            kill -9 $pid 2>/dev/null || true
            sleep 2
        fi
    fi
}

# Verificar puerto 8081
echo "Verificando disponibilidad de puertos..."
if ! check_port 8081; then
    echo -e "${YELLOW}⚠${NC} Puerto 8081 ocupado"
    echo "Intentando liberar..."
    kill_port 8081

    if ! check_port 8081; then
        echo -e "${YELLOW}⚠${NC} Puerto 8081 no se puede liberar, se buscará alternativo"
    fi
fi
echo ""

# Compilar clase
echo "Compilando mock..."
BUILD_DIR="$PROJECT_DIR/build/classes/java/test"
mkdir -p "$BUILD_DIR"

if [ ! -f "$BUILD_DIR/com/example/fileprocessor/mock/DocumentRestMock.class" ]; then
    if [ -f "$PROJECT_DIR/gradlew" ]; then
        echo "Compilando con Gradle..."
        cd "$PROJECT_DIR"
        ./gradlew testClasses --quiet 2>/dev/null || {
            echo -e "${RED}✗${NC} Error compilando con Gradle"
            exit 1
        }
    else
        echo "Compilando con javac..."
        "$JAVA_HOME/bin/javac" -cp "$BUILD_DIR" -d "$BUILD_DIR" \
            "$PROJECT_DIR/src/test/java/com/example/fileprocessor/mock/DocumentRestMock.java" 2>/dev/null || {
            echo -e "${RED}✗${NC} Error compilando. Intenta ejecutar: ./gradlew testClasses"
            exit 1
        }
    fi
fi

echo -e "${GREEN}✓${NC} Compilacion exitosa"
echo ""

# Agregar jackson-datatype-jsr310 al classpath si existe
JACKSON_JSR310=""
for jar in ~/.gradle/caches/modules-2/files-2.1/com.fasterxml.jackson.datatype/jackson-datatype-jsr310/*/jackson-datatype-jsr310-*.jar; do
    if [ -f "$jar" ]; then
        JACKSON_JSR310="$jar"
        break
    fi
done

# Buscar jackson-core también
JACKSON_CORE=""
for jar in ~/.gradle/caches/modules-2/files-2.1/com.fasterxml.jackson.core/jackson-core/*/jackson-core-*.jar; do
    if [ -f "$jar" ]; then
        JACKSON_CORE="$jar"
        break
    fi
done

JACKSON_DATABIND=""
for jar in ~/.gradle/caches/modules-2/files-2.1/com.fasterxml.jackson.core/jackson-databind/*/jackson-databind-*.jar; do
    if [ -f "$jar" ]; then
        JACKSON_DATABIND="$jar"
        break
    fi
done

# Construir classpath
CLASSPATH="$BUILD_DIR"
if [ -n "$JACKSON_JSR310" ]; then
    CLASSPATH="$CLASSPATH:$JACKSON_JSR310"
fi
if [ -n "$JACKSON_CORE" ]; then
    CLASSPATH="$CLASSPATH:$JACKSON_CORE"
fi
if [ -n "$JACKSON_DATABIND" ]; then
    CLASSPATH="$CLASSPATH:$JACKSON_DATABIND"
fi

# Iniciar servidor
echo "Iniciando servidor..."
cd "$PROJECT_DIR"

# Iniciar en segundo plano y capturar el puerto usado
exec "$JAVA_CMD" -cp "$CLASSPATH" "$MOCK_CLASS" "$@"