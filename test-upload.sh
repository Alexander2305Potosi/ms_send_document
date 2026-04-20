#!/bin/bash

# Script de prueba para subir archivos al servicio
# Uso: ./test-upload.sh /ruta/al/archivo.pdf

FILE_PATH="${1:-test.pdf}"

if [ ! -f "$FILE_PATH" ]; then
    echo "Creando archivo de prueba..."
    echo "Contenido de prueba para PDF" > "$FILE_PATH"
fi

echo "Enviando archivo: $FILE_PATH"
echo ""

curl -v -X POST http://localhost:8080/api/v1/files \
  -F "file=@$FILE_PATH" \
  -H "Accept: application/json"

echo ""
echo ""
echo "Si ves un error 415, verifica que:"
echo "1. El servicio esté corriendo en el puerto 8080"
echo "2. El mock SOAP esté corriendo en el puerto 8081"
echo "3. No estés enviando Content-Type manualmente (curl lo genera automáticamente con -F)"
