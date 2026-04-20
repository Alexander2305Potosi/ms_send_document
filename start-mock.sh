#!/bin/bash

# Script para iniciar el mock SOAP simple
# Usa el HttpServer built-in de Java (sin dependencias externas)

echo "Iniciando Mock SOAP Server..."
echo ""

cd "$(dirname "$0")"

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
    ./gradlew testClasses --quiet
}

echo "Iniciando servidor..."

# Ejecutar en segundo plano y guardar PID
nohup java -cp build/classes/java/test com.example.fileprocessor.mock.SimpleSoapMock > /dev/null 2>&1 &
NEW_PID=$!
echo $NEW_PID > .mock.pid

# Esperar a que el servidor inicie
sleep 2

# Verificar que realmente está corriendo
if kill -0 $NEW_PID 2>/dev/null && lsof -ti:8081 >/dev/null 2>&1; then
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
    rm -f .mock.pid
    exit 1
fi
