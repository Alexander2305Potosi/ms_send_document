# Instrucciones de uso - Postman

## ⚠️ IMPORTANTE: Configuración Manual Requerida

**Postman NO carga automáticamente los archivos al importar la colección.** Siempre debes seleccionarlos manualmente.

### Pasos después de importar:

1. Ve a la petición "Upload File - PDF" (o TXT, DOCX)
2. Abre la pestaña **Body**
3. Selecciona **form-data**
4. En el campo `file`, haz clic en **Select Files**
5. Elige un archivo de la carpeta `samples/`
6. Envía la petición

## Archivos de ejemplo

En la carpeta `samples/`:
- `sample.pdf` - Archivo PDF
- `sample.docx` - Documento Word
- `sample.txt` - Archivo texto

## Configuración correcta

### Headers (pestaña Headers)
- ✅ `Accept: application/json`
- ❌ NO agregar `Content-Type` manualmente

### Body (pestaña Body)
- Seleccionar: `form-data`
- Key: `file`
- Type: `File` (no Text)
- Value: seleccionar archivo de `samples/`

## Estructura de la colección

```
File Processor Service
├── ⚠️ IMPORTANTE - Configuración Inicial
│   ├── 1. Verificar Servicio
│   └── 2. Instrucciones de Configuración
├── Upload File - PDF
├── Upload File - DOCX
├── Upload File - TXT
├── Health Check
└── Upload File - Error (Invalid Extension)
```

## Troubleshooting

### "No file provided with key 'file'"
- ❌ No seleccionaste el archivo en Postman
- ✅ Solución: Ve a Body > form-data, haz clic en el campo file y selecciona un archivo

### Error 415
- ❌ Agregaste Content-Type manual
- ✅ Solución: Elimina el header Content-Type, Postman lo genera automáticamente

### Error 404
- ❌ Servicio no está corriendo
- ✅ Solución: Ejecuta `./gradlew bootRun --args='--spring.profiles.active=dev'`

### Error 500 / Timeout
- ❌ Mock SOAP no está corriendo
- ✅ Solución: Ejecuta `./start-mock.sh`
