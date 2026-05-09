# Plan de Mejora: Inferencia Centralizada de MIME Types

Este plan tiene como objetivo eliminar la redundancia de código y la "doble configuración" al detectar el tipo de contenido (`contentType`) de los archivos, utilizando las capacidades nativas de Spring Framework.

---

## 1. Objetivo
Simplificar la asignación de `contentType` eliminando los bloques de `if-else` manuales y permitiendo que el sistema reconozca nuevos formatos automáticamente basándose únicamente en la extensión del archivo, manteniendo la validación de seguridad a través de `filenamePattern`.

---

## 2. Estrategia Técnica
Se reemplazará el método manual `inferContentType` por el uso de `org.springframework.http.MediaTypeFactory`. Esta utilidad de Spring consulta un mapa interno estandarizado de extensiones y tipos MIME, lo que garantiza compatibilidad con cientos de formatos sin necesidad de modificar el código Java.

---

## 3. Análisis de Código (Antes vs Después)

### 3.1 `ZipDecompressor.java`

**Antes (Lógica Hardcoded):**
```java
private static String inferContentType(String filename) {
    String lower = filename.toLowerCase();
    if (lower.endsWith(".pdf")) {
        return "application/pdf";
    }
    if (lower.endsWith(".csv")) {
        return "text/csv";
    }
    if (lower.endsWith(".txt")) {
        return "text/plain";
    }
    if (lower.endsWith(".docx")) {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    }
    if (lower.endsWith(".xlsx")) {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }
    return "application/octet-stream";
}
```

**Después (Uso de `MediaTypeFactory`):**
```java
import org.springframework.http.MediaTypeFactory;

// ...
private static String inferContentType(String filename) {
    return MediaTypeFactory.getMediaType(filename)
            .map(Object::toString)
            .orElse("application/octet-stream");
}
```

---

## 4. Beneficios del Cambio
1.  **Mantenimiento Cero:** Si se agrega una nueva extensión permitida en el `filenamePattern` del `application.yml` (por ejemplo `.xml`), el sistema le asignará el `contentType` correcto automáticamente sin tocar el código.
2.  **Reducción de Deuda Técnica:** Eliminamos un método privado que requiere actualizaciones constantes.
3.  **Consistencia:** El sistema utilizará los estándares de la industria (IANA) para los tipos MIME.
