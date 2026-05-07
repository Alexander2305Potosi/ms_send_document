-- ============================================================================
-- File Processor Service - PostgreSQL Schema
-- ============================================================================
-- Usage:
--   psql -h <host> -U <user> -d <database> -f schema-postgresql.sql
-- ============================================================================

-- ============================================================================
-- Table: documentos
-- Stores document metadata and current processing state.
-- ============================================================================
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

CREATE INDEX IF NOT EXISTS idx_documentos_estado ON documentos (estado);
CREATE INDEX IF NOT EXISTS idx_documentos_documento_id ON documentos (id_documento);
CREATE INDEX IF NOT EXISTS idx_documentos_producto_id ON documentos (id_producto);
CREATE INDEX IF NOT EXISTS idx_documentos_caso_uso ON documentos (caso_uso);

-- ============================================================================
-- Table: historico_documentos
-- Append-only traceability / audit log. Each row records one operation
-- (SYNC, SOAP, S3) with its outcome.
-- ============================================================================
CREATE TABLE IF NOT EXISTS historico_documentos (
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

CREATE INDEX IF NOT EXISTS idx_historico_documento_id ON historico_documentos (documento_id);
CREATE INDEX IF NOT EXISTS idx_historico_doc_operacion ON historico_documentos (documento_id, operacion, fecha_creacion DESC);

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

-- ============================================================================
-- Table: categoria_manual
-- ============================================================================
CREATE TABLE IF NOT EXISTS categoria_manual (
    id                  BIGSERIAL       PRIMARY KEY,
    categoria           VARCHAR(255)    NOT NULL UNIQUE,
    descripcion_manual  VARCHAR(500)    NOT NULL,
    fecha_vigencia      DATE,
    fecha_creacion      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cat_categoria_vigencia ON categoria_manual (categoria, fecha_vigencia);

-- ============================================================================
-- Table: pais_homologado
-- ============================================================================
CREATE TABLE IF NOT EXISTS pais_homologado (
    id              BIGSERIAL       PRIMARY KEY,
    pais            VARCHAR(255)    NOT NULL UNIQUE,
    pais_homologado VARCHAR(255)    NOT NULL,
    fecha_creacion  TIMESTAMP       NOT NULL DEFAULT NOW()
);
