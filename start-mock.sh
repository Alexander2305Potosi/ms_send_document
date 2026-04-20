#!/bin/bash

# Script para iniciar el mock SOAP simple
# Usa el HttpServer built-in de Java (sin dependencias externas)

echo "Iniciando Mock SOAP Server..."
echo ""

# Compilar y ejecutar directamente
cd "$(dirname "$0")"

# Compilar
javac -d build/classes/java/test src/test/java/com/example/fileprocessor/mock/SimpleSoapMock.java

# Ejecutar
java -cp build/classes/java/test com.example.fileprocessor.mock.SimpleSoapMock
