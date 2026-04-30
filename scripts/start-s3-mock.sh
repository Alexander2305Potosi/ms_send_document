#!/bin/bash
# Script para iniciar el Mock S3 (LocalStack-like)

# Detectar SO
OS="$(uname -s)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Buscar Java
find_java() {
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        echo "$JAVA_HOME/bin/java"
        return
    fi

    if [ -x "/usr/bin/java" ]; then
        echo "/usr/bin/java"
        return
    fi

    # macOS Homebrew
    if [ -x "/usr/local/opt/openjdk@21/bin/java" ]; then
        echo "/usr/local/opt/openjdk@21/bin/java"
        return
    fi

    if [ -x "/usr/local/opt/openjdk/bin/java" ]; then
        echo "/usr/local/opt/openjdk/bin/java"
        return
    fi

    echo "java"
}

JAVA_CMD=$(find_java)
echo "Usando Java: $JAVA_CMD"

# Compilar si es necesario
echo "Compilando clases de test..."
cd "$PROJECT_DIR"
./gradlew testClasses --quiet 2>/dev/null

if [ $? -ne 0 ]; then
    echo "Error compilando. Verificando build..."
    ./gradlew compileTestJava --quiet 2>&1 | tail -5
fi

# Puerto y bucket opcional
PORT="${1:-4566}"
BUCKET="${2:-documents-bucket}"

echo ""
echo "Iniciando S3 Mock en puerto $PORT..."
echo ""

# Iniciar mock
exec $JAVA_CMD -cp "$PROJECT_DIR/build/classes/java/test" \
    com.example.fileprocessor.mock.S3Mock "$PORT" "$BUCKET"
