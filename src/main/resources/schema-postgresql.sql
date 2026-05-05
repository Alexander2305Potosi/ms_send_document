-- ============================================================================
-- File Processor Service - PostgreSQL Schema
-- ============================================================================
-- Usage:
--   psql -h <host> -U <user> -d <database> -f schema-postgresql.sql
-- ============================================================================

-- ============================================================================
-- Table: historico_documentos
-- Unified table storing both document metadata and processing history.
-- ============================================================================
CREATE TABLE IF NOT EXISTS historico_documentos (
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
    resultado           VARCHAR(50),
    codigo_error        VARCHAR(50),
    reintentos          INTEGER         NOT NULL DEFAULT 0,
    fecha_creacion      TIMESTAMP       NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_historico_documento_id ON historico_documentos (id_documento);
CREATE INDEX IF NOT EXISTS idx_historico_estado ON historico_documentos (estado);
CREATE INDEX IF NOT EXISTS idx_historico_producto_id ON historico_documentos (id_producto);
CREATE INDEX IF NOT EXISTS idx_historico_documento_caso_uso ON historico_documentos (id_documento, caso_uso);
CREATE INDEX IF NOT EXISTS idx_historico_fecha_creacion ON historico_documentos (fecha_creacion DESC);

-- ============================================================================
-- Table: productos
-- Stores products synced from the external REST API.
-- ============================================================================
CREATE TABLE IF NOT EXISTS productos (
    id              BIGSERIAL       PRIMARY KEY,
    id_producto     VARCHAR(255)    NOT NULL,
    nombre          VARCHAR(500)    NOT NULL,
    fecha_carga     TIMESTAMP       NOT NULL DEFAULT NOW(),
    estado          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    mensaje_error   VARCHAR(2000)
);

CREATE INDEX IF NOT EXISTS idx_prod_estado       ON productos (estado);
CREATE INDEX IF NOT EXISTS idx_prod_fecha_carga  ON productos (fecha_carga);
CREATE INDEX IF NOT EXISTS idx_prod_producto_id  ON productos (id_producto);
CREATE INDEX IF NOT EXISTS idx_prod_carga_estado ON productos (fecha_carga, estado);
