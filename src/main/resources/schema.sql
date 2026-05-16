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
    codigo_pais VARCHAR(10) UNIQUE,
    nombre VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS categoria_manual (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) UNIQUE
);

-- Data for Local DB
MERGE INTO pais_homologado (codigo_pais, nombre) KEY (codigo_pais) VALUES ('AR', 'Argentina');
MERGE INTO pais_homologado (codigo_pais, nombre) KEY (codigo_pais) VALUES ('CO', 'Colombia');
