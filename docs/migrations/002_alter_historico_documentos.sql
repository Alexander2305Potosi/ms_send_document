-- Migration: Remove redundant resultado column and ensure estado exists

-- Eliminar columna redundante resultado (status)
ALTER TABLE historico_documentos
    DROP COLUMN IF EXISTS resultado;

-- Asegurar que estado (state) existe
ALTER TABLE historico_documentos
    ADD COLUMN IF NOT EXISTS estado VARCHAR(100);
