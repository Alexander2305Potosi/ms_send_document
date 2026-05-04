-- Migration: Create documento and historico_documentos tables
-- Replaces old productos and historico_documentos tables

CREATE TABLE IF NOT EXISTS documento (
    id BIGSERIAL PRIMARY KEY,
    id_document VARCHAR(100) NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    doc_key VARCHAR(255),
    name VARCHAR(255),
    owner VARCHAR(255),
    path TEXT,
    status VARCHAR(50) NOT NULL,
    version_contract VARCHAR(50),
    state VARCHAR(100) NOT NULL,
    error_message TEXT,
    is_zip BOOLEAN DEFAULT FALSE,
    parent_zip_name VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_documento_status ON documento(status);
CREATE INDEX IF NOT EXISTS idx_documento_product_id ON documento(product_id);
CREATE INDEX IF NOT EXISTS idx_documento_document_id ON documento(id_document);

CREATE TABLE IF NOT EXISTS historico_documentos (
    id BIGSERIAL PRIMARY KEY,
    document_id VARCHAR(100) NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    use_case VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    error_code VARCHAR(50),
    error_message TEXT,
    retry INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_historico_document_id ON historico_documentos(document_id);
CREATE INDEX IF NOT EXISTS idx_historico_document_use_case ON historico_documentos(document_id, use_case);

-- Remove old tables if they exist (run after verifying data migration)
-- DROP TABLE IF EXISTS historico_documentos_old;
-- DROP TABLE IF EXISTS productos_old;