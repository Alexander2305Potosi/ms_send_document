-- ============================================================================
-- File Processor Service - PostgreSQL Schema
-- ============================================================================
-- Usage:
--   psql -h <host> -U <user> -d <database> -f schema-postgresql.sql
-- ============================================================================

-- ============================================================================
-- Table: historico_documentos
-- Stores traceability records for every document upload attempt.
-- ============================================================================
CREATE TABLE IF NOT EXISTS historico_documentos (
    id              BIGSERIAL       PRIMARY KEY,
    id_producto     VARCHAR(255)    NOT NULL,
    id_documento    VARCHAR(500)    NOT NULL,
    nombre_archivo  VARCHAR(500)    NOT NULL,
    nombre_comprimido VARCHAR(500),
    estado          VARCHAR(20)     NOT NULL DEFAULT 'FAILURE',
    codigo_error    VARCHAR(100),
    razon_fallo     VARCHAR(2000),
    numero_intentos INT             NOT NULL DEFAULT 1,
    fecha_envio     TIMESTAMP,
    fecha_fallo     TIMESTAMP,
    fecha_creacion  TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_hist_producto   ON historico_documentos (id_producto);
CREATE INDEX IF NOT EXISTS idx_hist_estado     ON historico_documentos (estado);
CREATE INDEX IF NOT EXISTS idx_hist_created    ON historico_documentos (fecha_creacion DESC);

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

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_prod_estado       ON productos (estado);
CREATE INDEX IF NOT EXISTS idx_prod_fecha_carga  ON productos (fecha_carga);
CREATE INDEX IF NOT EXISTS idx_prod_producto_id  ON productos (id_producto);

-- Composite index for the main query: find PENDING products loaded today
CREATE INDEX IF NOT EXISTS idx_prod_carga_estado ON productos (fecha_carga, estado);
