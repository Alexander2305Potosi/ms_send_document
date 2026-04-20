#!/bin/bash

# Script para iniciar el mock SOAP server
# Requiere: Java 21 y el proyecto compilado

cd "$(dirname "$0")"

echo "Compiling mock server..."
./gradlew testClasses --quiet

echo ""
echo "Starting SOAP Mock Server on port 8081..."
echo "Endpoint: http://localhost:8081/soap/fileservice"
echo ""
echo "Success response will be returned for all requests."
echo "Press Ctrl+C to stop"
echo ""

# Create classpath
TEST_CP=$(./gradlew -q printTestClasspath 2>/dev/null || echo "")
if [ -z "$TEST_CP" ]; then
    # Fallback - common gradle cache locations
    TEST_CP="build/classes/java/test:build/classes/java/main:build/resources/main:build/resources/test"
fi

java -cp "$TEST_CP" com.example.fileprocessor.mock.SoapMockServer
