-- Migration: Create historico_documentos table (unified)

CREATE TABLE IF NOT EXISTS historico_documentos (
    id BIGSERIAL PRIMARY KEY,
    id_documento VARCHAR(100) NOT NULL,
    id_producto VARCHAR(100) NOT NULL,
    activo BOOLEAN DEFAULT TRUE,
    clave_documento VARCHAR(255),
    nombre VARCHAR(255),
    propietario VARCHAR(255),
    ruta TEXT,
    estado VARCHAR(100) NOT NULL,
    version_contrato VARCHAR(50),
    mensaje_error TEXT,
    es_zip BOOLEAN DEFAULT FALSE,
    nombre_zip_padre VARCHAR(255),
    caso_uso VARCHAR(100),
    resultado VARCHAR(50),
    codigo_error VARCHAR(50),
    reintentos INTEGER NOT NULL DEFAULT 0,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_historico_documento_id ON historico_documentos(id_documento);
CREATE INDEX IF NOT EXISTS idx_historico_estado ON historico_documentos(estado);
CREATE INDEX IF NOT EXISTS idx_historico_producto_id ON historico_documentos(id_producto);
CREATE INDEX IF NOT EXISTS idx_historico_documento_caso_uso ON historico_documentos(id_documento, caso_uso);

-- Remove old tables if they exist (run after verifying data migration)
-- DROP TABLE IF EXISTS documento;
