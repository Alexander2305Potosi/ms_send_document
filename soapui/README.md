# SOAP UI Mock Service

## Archivos incluidos

- `FileService-Mock-soapui-project.xml` - Proyecto SOAP UI con WSDL embebido
- `FileService.wsdl` - WSDL del servicio (referencia)

## Instrucciones de uso

### 1. Importar el proyecto

1. Abre SOAP UI
2. File → Import Project
3. Selecciona `FileService-Mock-soapui-project.xml`

### 2. Iniciar el Mock Service

1. En el panel izquierdo, expande el proyecto
2. Verás "FileService Mock" bajo "Mock Services"
3. Haz clic derecho → **Start**

El mock se iniciará en `http://localhost:8081/soap/fileservice`

### 3. Verificar que funciona

El proyecto ahora incluye el **WSDL embebido**, por lo que no necesita cargar nada de una URL externa.

## Respuestas disponibles

El mock está configurado con dispatch **SEQUENCE**, lo que significa que las respuestas se devuelven en orden:

1. **Success Response** (200) - Primera petición
2. **Server Error 500** (500) - Segunda petición
3. **Service Unavailable 503** (503) - Tercera petición
4. **Gateway Timeout 504** (504) - Cuarta petición
5. **Slow Response** (200 con delay de 30s) - Quinta petición
6. **Bad Request 400** (400) - Sexta petición

## Solución de problemas

### "Cannot invoke WsdlOperation.getAction() because wsdlOperation is null"

**Causa**: El proyecto anterior no tenía el WSDL embebido correctamente.

**Solución**: Usa el archivo actualizado `FileService-Mock-soapui-project.xml` que incluye el WSDL embebido.

## Diferencias con el Mock Java

| Característica | SOAP UI Mock | Mock Java (SimpleSoapMock) |
|---------------|--------------|----------------------------|
| Configuración | WSDL embebido | Sin configuración necesaria |
| Respuestas | Múltiples (secuencia) | Solo éxito (200) |
| Escenarios de error | Sí (500, 503, 504, etc.) | No - solo éxito |
| Requiere SOAP UI instalado | Sí | No - usa Java built-in |

## Recomendación

- Usa **SOAP UI** si necesitas probar escenarios de error específicos
- Usa el **Mock Java** para pruebas rápidas y simples
