#!/bin/bash

# Script para verificar que Postman está enviando los datos correctamente
# Este script simula exactamente lo que Postman debería enviar

echo "=========================================="
echo "Test de simulación Postman"
echo "=========================================="
echo ""

# Crear un archivo de prueba temporal
echo "Contenido de prueba" > /tmp/test_postman.txt

echo "Enviando request tipo Postman..."
echo ""

# Simular exactamente lo que Postman envía
curl -v http://localhost:8080/api/v1/files \
  -X POST \
  -H "Accept: application/json" \
  -H "User-Agent: PostmanRuntime/7.36.1" \
  -F "file=@/tmp/test_postman.txt"

echo ""
echo ""
echo "=========================================="
echo "Si esto funciona, Postman debería funcionar"
echo "Si no funciona, hay un problema en el servidor"
echo "=========================================="

# Limpiar
rm /tmp/test_postman.txt
