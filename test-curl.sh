#!/bin/bash

# Script de prueba para subir archivos
curl -X POST http://localhost:8080/api/v1/files -F "file=@postman/samples/sample.txt" -H "Accept: application/json"
echo ""
