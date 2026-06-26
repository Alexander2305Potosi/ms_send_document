# Guía de Reportes y Consultas SQL (Reportes Administrativos)

Esta guía contiene consultas SQL optimizadas para la base de datos del microservicio (H2 en desarrollo, PostgreSQL en producción). Los reportes están diseñados para ser interpretados por personal administrativo, convirtiendo los códigos de error técnicos en descripciones claras en lenguaje de negocios.

---

## 1. Reporte de Documentos Rechazados por Reglas de Negocio
Este reporte muestra los documentos que fueron rechazados porque no cumplen con las políticas del negocio (como tamaño límite superado, extensión de archivo inválida o contenido vacío). Estos casos **requieren corrección del archivo** y no se solucionan con reintentos de red.

```sql
SELECT 
    d.id_documento AS "Código del Documento",
    h.nombre_archivo AS "Nombre del Archivo",
    d.sucursal AS "Sucursal",
    CASE h.codigo_error
        WHEN 'PATTERN_MISMATCH' THEN 'Formato de archivo no permitido (extensión o tipo MIME incorrecto)'
        WHEN 'SIZE_EXCEEDED' THEN 'El archivo supera el tamaño máximo permitido'
        WHEN 'INVALID_BASE64' THEN 'El contenido del archivo está corrupto (decodificación Base64 fallida)'
        WHEN 'EMPTY_CONTENT' THEN 'El archivo está vacío (0 bytes)'
        WHEN 'DECOMPRESSION_ERROR' THEN 'El archivo comprimido ZIP está corrupto o protegido con contraseña'
        ELSE 'Incumplimiento de regla de negocio general'
    END AS "Motivo del Rechazo",
    h.fecha_fin AS "Fecha de Rechazo"
FROM documentos d
JOIN historico_documentos h ON d.id = h.documento_id
WHERE d.estado = 'BUSINESS_REJECTION' 
   OR h.codigo_error IN ('PATTERN_MISMATCH', 'SIZE_EXCEEDED', 'INVALID_BASE64', 'EMPTY_CONTENT', 'DECOMPRESSION_ERROR')
ORDER BY h.fecha_fin DESC;
```

---

## 2. Reporte de Fallos de Envío o Conexión (Errores de API)
Este reporte lista las transacciones que fallaron debido a problemas en los servidores externos de origen o destino (como caídas de red, indisponibilidad del servicio SOAP/S3 o timeouts). 

```sql
SELECT 
    d.id_documento AS "Código del Documento",
    h.nombre_archivo AS "Nombre del Archivo",
    d.caso_uso AS "Canal de Envío (SOAP/S3)",
    d.sucursal AS "Sucursal",
    h.reintentos AS "Intento de Envío",
    CASE h.codigo_error
        WHEN 'GATEWAY_TIMEOUT' THEN 'Tiempo de espera agotado (el servidor destino tardó demasiado en responder)'
        WHEN 'BAD_GATEWAY' THEN 'Error en el servidor de destino (respondió con error técnico 5xx)'
        WHEN 'SERVICE_UNAVAILABLE' THEN 'El servicio externo está fuera de línea temporalmente'
        WHEN 'SOURCE_NOT_FOUND' THEN 'El documento no existe en el sistema que origina el archivo (Error 404)'
        WHEN 'SOURCE_RATE_LIMIT' THEN 'Saturación en el origen (Límite de peticiones por minuto superado)'
        WHEN 'DEST_BAD_REQUEST' THEN 'Los datos del documento fueron rechazados por el destino (Error 400)'
        WHEN 'DEST_UNAUTHORIZED' THEN 'Problema de acceso (Credenciales o permisos incorrectos en el destino)'
        WHEN 'UNKNOWN_ERROR' THEN 'Error de sistema no clasificado'
        ELSE 'Fallo de comunicación con la API'
    END AS "Descripción del Problema",
    h.mensaje_error AS "Detalle Técnico para Soporte",
    h.fecha_fin AS "Fecha del Fallo"
FROM documentos d
JOIN historico_documentos h ON d.id = h.documento_id
WHERE h.resultado = 'FAILURE'
  AND h.codigo_error NOT IN ('PATTERN_MISMATCH', 'SIZE_EXCEEDED', 'INVALID_BASE64', 'EMPTY_CONTENT', 'DECOMPRESSION_ERROR')
ORDER BY h.fecha_fin DESC;
```

---

## 3. Panel de Control General (Resumen para Administrativos)
Esta consulta genera un estado consolidado y limpio de todos los documentos y paquetes procesados, facilitando el seguimiento del estado del trámite de manera no técnica.

```sql
SELECT 
    d.id_documento AS "Documento Principal",
    COALESCE(h.nombre_archivo, d.name) AS "Nombre del Archivo",
    d.sucursal AS "Sucursal",
    CASE 
        WHEN d.estado = 'PROCESSED' THEN 'Completado con Éxito'
        WHEN d.estado = 'BUSINESS_REJECTION' THEN 'Rechazado (Requiere corregir archivo)'
        WHEN d.estado = 'FAILED' THEN 'Fallido Definitivo (Error de Conexión)'
        WHEN d.estado = 'PENDING' THEN 'En Espera (Pendiente de procesar o reintentar)'
        WHEN d.estado = 'IN_PROGRESS' THEN 'En Proceso de Envío'
        ELSE d.estado
    END AS "Estado del Trámite",
    CASE 
        WHEN h.resultado = 'SUCCESS' THEN 'Ninguna (Trámite finalizado con éxito)'
        WHEN h.codigo_error IN ('PATTERN_MISMATCH', 'SIZE_EXCEEDED', 'INVALID_BASE64', 'EMPTY_CONTENT', 'DECOMPRESSION_ERROR') 
            THEN 'Revisar y corregir archivo (tamaño, nombre o formato) y volver a subir'
        ELSE 'Esperar reintento automático de red o reportar a soporte técnico'
    END AS "Acción Requerida",
    d.fecha_actualizacion AS "Última Actualización"
FROM documentos d
LEFT JOIN (
    SELECT documento_id, nombre_archivo, resultado, codigo_error, fecha_fin,
           ROW_NUMBER() OVER (PARTITION BY documento_id, nombre_archivo ORDER BY fecha_fin DESC) as rn
    FROM historico_documentos
) h ON d.id = h.documento_id AND h.rn = 1
ORDER BY d.fecha_actualizacion DESC;
```
