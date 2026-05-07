-- ============================================================================
-- Migration 005: Split historico_documentos into documentos + historico_documentos
-- ============================================================================
-- This migration:
--   1. Creates the new `documentos` table (metadata + current state)
--   2. Migrates distinct metadata rows from `historico_documentos` to `documentos`
--   3. Creates the new `historico_documentos` table (traceability only)
--   4. Migrates traceability rows with FK references
--   5. Drops the old `historico_documentos` table
-- ============================================================================

-- Step 1: Create documentos table
CREATE TABLE IF NOT EXISTS documentos (
    id                  BIGSERIAL       PRIMARY KEY,
    id_documento        VARCHAR(100)    NOT NULL,
    id_producto         VARCHAR(100)    NOT NULL,
    activo              BOOLEAN         DEFAULT TRUE,
    clave_documento     VARCHAR(255),
    nombre              VARCHAR(255),
    propietario         VARCHAR(255),
    ruta                TEXT,
    estado              VARCHAR(100)    NOT NULL,
    version_contrato    VARCHAR(50),
    mensaje_error       TEXT,
    es_zip              BOOLEAN         DEFAULT FALSE,
    nombre_zip_padre    VARCHAR(255),
    caso_uso            VARCHAR(100),
    fecha_creacion      TIMESTAMP       NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Step 2: Migrate metadata (rows with caso_uso IS NULL) to documentos
-- Each distinct id_documento gets its latest metadata row
INSERT INTO documentos (id_documento, id_producto, activo, clave_documento, nombre,
    propietario, ruta, estado, version_contrato, mensaje_error, es_zip,
    nombre_zip_padre, caso_uso, fecha_creacion, fecha_actualizacion)
SELECT DISTINCT ON (id_documento)
    id_documento, id_producto, activo, clave_documento, nombre,
    propietario, ruta, estado, version_contrato, mensaje_error, es_zip,
    nombre_zip_padre, NULL, fecha_creacion, fecha_actualizacion
FROM historico_documentos
WHERE caso_uso IS NULL
ORDER BY id_documento, fecha_actualizacion DESC;

-- Step 3: Create new historico_documentos table (traceability only)
CREATE TABLE IF NOT EXISTS historico_documentos_new (
    id                  BIGSERIAL       PRIMARY KEY,
    documento_id        BIGINT          NOT NULL REFERENCES documentos(id),
    nombre_archivo      VARCHAR(255),
    operacion           VARCHAR(50),
    message_id          VARCHAR(100),
    resultado           VARCHAR(50),
    codigo_error        VARCHAR(50),
    mensaje_error       TEXT,
    stack_trace         TEXT,
    reintentos          INTEGER         NOT NULL DEFAULT 0,
    fecha_inicio        TIMESTAMP,
    fecha_fin           TIMESTAMP,
    fecha_creacion      TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Step 4: Migrate traceability rows (caso_uso IS NOT NULL) to new table
INSERT INTO historico_documentos_new (documento_id, nombre_archivo, operacion,
    message_id, resultado, codigo_error, mensaje_error, stack_trace, reintentos,
    fecha_inicio, fecha_fin, fecha_creacion)
SELECT
    d.id,
    h.nombre,
    h.operacion,
    h.message_id,
    h.resultado,
    h.codigo_error,
    h.mensaje_error,
    h.stack_trace,
    h.reintentos,
    h.fecha_inicio,
    h.fecha_fin,
    h.fecha_creacion
FROM historico_documentos h
JOIN documentos d ON d.id_documento = h.id_documento
WHERE h.caso_uso IS NOT NULL;

-- Step 5: Drop old table and rename new one
DROP TABLE IF EXISTS historico_documentos;
ALTER TABLE historico_documentos_new RENAME TO historico_documentos;

-- Step 6: Create indexes
CREATE INDEX IF NOT EXISTS idx_documentos_estado ON documentos (estado);
CREATE INDEX IF NOT EXISTS idx_documentos_documento_id ON documentos (id_documento);
CREATE INDEX IF NOT EXISTS idx_documentos_producto_id ON documentos (id_producto);
CREATE INDEX IF NOT EXISTS idx_documentos_caso_uso ON documentos (caso_uso);
CREATE INDEX IF NOT EXISTS idx_historico_documento_id ON historico_documentos (documento_id);
CREATE INDEX IF NOT EXISTS idx_historico_doc_operacion ON historico_documentos (documento_id, operacion, fecha_creacion DESC);
