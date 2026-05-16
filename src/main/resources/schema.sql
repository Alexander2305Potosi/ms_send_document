-- Schema for Local DB (fileprocessor)
CREATE TABLE IF NOT EXISTS documentos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_documento VARCHAR(100),
    id_producto VARCHAR(100),
    nombre_documento VARCHAR(255),
    estado_sincronizacion VARCHAR(50),
    mensaje_sincronizacion CLOB,
    es_zip BOOLEAN DEFAULT FALSE,
    caso_uso VARCHAR(50),
    carpeta_origen VARCHAR(255),
    pais_origen VARCHAR(10),
    reintentos INT DEFAULT 0,
    fecha_carga TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fecha_carga_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS historico_documentos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_documentos BIGINT,
    nombre_documento VARCHAR(255),
    caso_uso VARCHAR(50),
    resultado VARCHAR(50),
    estado_sincronizacion VARCHAR(100),
    mensaje_sincronizacion CLOB,
    reintentos INT DEFAULT 0,
    fecha_inicio_procesamiento TIMESTAMP,
    fecha_fin_procesamiento TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pais_homologado (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    carpeta_origen VARCHAR(255),
    pais_origen VARCHAR(10),
    carpeta_homologada VARCHAR(255),
    pais_homologado VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS categoria_manual (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prefijo VARCHAR(100) UNIQUE,
    categoria_documento VARCHAR(100)
);

-- Data for Local DB
-- 10 Scenarios for pais_homologado
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('/mnt/shared/docs/standard', 'CO', 'STANDARD_DOCS', 'Colombia');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('/mnt/shared/docs/large', 'CO', 'LARGE_DOCS', 'Colombia');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('/mnt/shared/docs/word', 'CO', 'WORD_DOCS', 'Colombia');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('/mnt/shared/docs/text', 'CO', 'TEXT_DOCS', 'Colombia');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('/mnt/shared/docs/zip', 'CO', 'ZIP_DOCS', 'Colombia');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('/mnt/network/retry', 'AR', 'RETRY_DOCS', 'Argentina');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('/var/data/invalid', 'CO', 'INVALID_DOCS', 'Colombia');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('/opt/tech/errors', 'AR', 'ERROR_DOCS', 'Argentina');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('/mnt/shared/docs/other', 'PE', 'OTHER_DOCS', 'Peru');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('/mnt/shared/docs/images', 'CL', 'IMAGE_DOCS', 'Chile');

-- Scenarios for categoria_manual
INSERT INTO categoria_manual (prefijo, categoria_documento) VALUES ('SC-', 'electrodomestico');
INSERT INTO categoria_manual (prefijo, categoria_documento) VALUES ('DOC-', 'ropa');
INSERT INTO categoria_manual (prefijo, categoria_documento) VALUES ('TXT-', 'aseo');
INSERT INTO categoria_manual (prefijo, categoria_documento) VALUES ('ZIP-', 'zapatos');
