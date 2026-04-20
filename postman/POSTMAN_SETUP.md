# Configuración de Postman - Paso a Paso

## Importante: Seleccionar el archivo después de importar

Cuando importas la colección, Postman NO carga automáticamente los archivos. Debes seleccionarlos manualmente:

### Paso 1: Abrir la petición "Upload File - PDF"

### Paso 2: Ir a la pestaña "Body"

### Paso 3: Verificar configuración
Asegúrate de que:
- ✅ Esté seleccionado **"form-data"** (no "raw", ni "x-www-form-urlencoded")
- ✅ La key sea **"file"** (exactamente, sin comillas)
- ✅ El tipo sea **"File"** (no "Text")

### Paso 4: Seleccionar el archivo
1. En la fila de "file", haz clic en la columna **"Value"**
2. Selecciona **"Select Files"**
3. Navega a `postman/samples/sample.pdf` (o cualquier otro archivo)
4. Selecciona el archivo

### Paso 5: Verificar Headers
Ve a la pestaña "Headers" y asegúrate de:
- ✅ NO haya un header "Content-Type" manual
- ✅ Solo esté el header "Accept: application/json"

### Paso 6: Enviar
Haz clic en **Send**

---

## Screenshots de referencia

```
Body (form-data):
┌─────────┬────────┬─────────────┬─────────────────────────────┐
│  KEY    │ VALUE  │   TYPE      │  DESCRIPTION                │
├─────────┼────────┼─────────────┼─────────────────────────────┤
│  file   │ [arch] │  ▼ File     │ ← Debe decir "File"         │
└─────────┴────────┴─────────────┴─────────────────────────────┘
                              ↑
                    Click aquí para seleccionar archivo
```

```
Headers:
┌─────────────────┬──────────────────────────────────────────────┐
│ Accept          │ application/json    ✅                       │
│ Content-Type    │ multipart/form-data ❌ (NO agregar manual)   │
└─────────────────┴──────────────────────────────────────────────┘
```

---

## Si sigue sin funcionar

### Verifica que no haya headers manuales:
1. Ve a la pestaña Headers
2. Si ves "Content-Type", elimínalo
3. Activa el toggle "Hidden" para ver todos los headers que Postman envía

### Verifica la URL:
Debe ser exactamente: `http://localhost:8080/api/v1/files`

### Verifica que el método sea POST:
No GET, ni PUT, ni PATCH

### Prueba con curl primero:
```bash
./test-curl.sh
```
Si curl funciona, el problema es específico de Postman.

---

## Error común: "No file provided with key 'file'"

Esto significa que:
1. El body no está en modo "form-data"
2. La key no es exactamente "file"
3. El tipo no es "File" (está como "Text")
4. No se ha seleccionado ningún archivo

Revisa cuidadosamente cada uno de estos puntos.
