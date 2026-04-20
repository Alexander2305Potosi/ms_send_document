#!/bin/bash

# Script para iniciar el mock SOAP simple
# Usa el HttpServer built-in de Java (sin dependencias externas)

echo "Iniciando Mock SOAP Server..."
echo ""

cd "$(dirname "$0")"

# Configurar Java (buscar instalaciones comunes)
if [ -z "$JAVA_HOME" ]; then
    # Buscar Java en ubicaciones comunes
    if [ -d "/opt/java/jdk-21.0.2+13/Contents/Home" ]; then
        export JAVA_HOME="/opt/java/jdk-21.0.2+13/Contents/Home"
    elif [ -d "/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home" ]; then
        export JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
    elif [ -d "/usr/local/opt/openjdk@21" ]; then
        export JAVA_HOME="/usr/local/opt/openjdk@21"
    elif command -v /usr/libexec/java_home >/dev/null 2>&1; then
        export JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null)
    fi
fi

# Verificar que Java está disponible
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "Java encontrado en: $JAVA_HOME"
    java -version 2>&1 | head -1
else
    echo "ADVERTENCIA: No se pudo detectar JAVA_HOME. Usando java del PATH..."
fi
echo ""

# Verificar si el puerto está ocupado
echo "Verificando puerto 8081..."
PID=$(lsof -ti:8081 2>/dev/null)
if [ -n "$PID" ]; then
    echo "ADVERTENCIA: El puerto 8081 está ocupado por PID: $PID"
    echo "Proceso: $(ps -p $PID -o comm= 2>/dev/null || echo 'desconocido')"
    echo "Intentando detener el proceso..."

    kill -9 $PID 2>/dev/null
    sleep 2

    # Verificar nuevamente
    PID=$(lsof -ti:8081 2>/dev/null)
    if [ -n "$PID" ]; then
        echo "ERROR: No se pudo liberar el puerto 8081. Proceso aún activo: $PID"
        echo "Intenta manualmente: kill -9 $PID"
        exit 1
    fi
    echo "Puerto liberado."
fi

echo ""
echo "Compilando..."

# Compilar
javac -d build/classes/java/test src/test/java/com/example/fileprocessor/mock/SimpleSoapMock.java 2>/dev/null || {
    echo "Compilando con gradle..."
    # Detectar si estamos en Windows (Git Bash, Cygwin, MSYS)
    if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "win32" ]]; then
        ./gradlew.bat testClasses --quiet 2>/dev/null || gradlew.bat testClasses --quiet
    else
        ./gradlew testClasses --quiet
    fi
}

echo "Iniciando servidor..."

# Ejecutar en segundo plano y guardar PID
nohup java -cp build/classes/java/test com.example.fileprocessor.mock.SimpleSoapMock > /dev/null 2>&1 &
NEW_PID=$!
echo $NEW_PID > .mock.pid

# Esperar a que el servidor inicie
sleep 2

# Verificar que realmente está corriendo
sleep 3
PID_CHECK=$(lsof -ti:8081 2>/dev/null || netstat -ano 2>/dev/null | grep ":8081" | awk '{print $5}' | head -1)

if [ -n "$PID_CHECK" ] || kill -0 $NEW_PID 2>/dev/null; then
    echo ""
    echo "========================================"
    echo "Mock iniciado correctamente!"
    echo "PID: $NEW_PID"
    echo "Endpoint: http://localhost:8081/soap/fileservice"
    echo "========================================"
    echo ""
    echo "Para detenerlo: ./stop-mock.sh"
else
    echo "ERROR: El mock no pudo iniciar correctamente."
    echo "Verifica que Java esté instalado y disponible."
    rm -f .mock.pid
    exit 1
fi
