-- Migration: Add sequences for all tables

-- historico_documentos
CREATE SEQUENCE IF NOT EXISTS historico_documentos_id_seq AS integer;
ALTER TABLE historico_documentos ALTER COLUMN id_historico_documentos SET DEFAULT nextval('historico_documentos_id_seq');
ALTER SEQUENCE historico_documentos_id_seq OWNED BY historico_documentos.id_historico_documentos;

CREATE SEQUENCE IF NOT EXISTS productos_id_seq AS integer;
ALTER TABLE productos ALTER COLUMN id SET DEFAULT nextval('productos_id_seq');
ALTER SEQUENCE productos_id_seq OWNED BY productos.id;

CREATE SEQUENCE IF NOT EXISTS categoria_manual_id_seq AS integer;
ALTER TABLE categoria_manual ALTER COLUMN id SET DEFAULT nextval('categoria_manual_id_seq');
ALTER SEQUENCE categoria_manual_id_seq OWNED BY categoria_manual.id;

CREATE SEQUENCE IF NOT EXISTS pais_homologado_id_seq AS integer;
ALTER TABLE pais_homologado ALTER COLUMN id SET DEFAULT nextval('pais_homologado_id_seq');
ALTER SEQUENCE pais_homologado_id_seq OWNED BY pais_homologado.id;
