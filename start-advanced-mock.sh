#!/bin/bash

# Script para iniciar el mock SOAP avanzado con múltiples respuestas

echo "Iniciando Advanced SOAP Mock Server..."
echo ""

cd "$(dirname "$0")"

# Compilar
javac -d build/classes/java/test src/test/java/com/example/fileprocessor/mock/AdvancedSoapMock.java

# Ejecutar
java -cp build/classes/java/test com.example.fileprocessor.mock.AdvancedSoapMock
