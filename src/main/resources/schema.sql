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
    pais_origen VARCHAR(100),
    carpeta_homologada VARCHAR(255),
    pais_homologado VARCHAR(100),
    categoria_homologado VARCHAR(100),
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
    pais_origen VARCHAR(100),
    carpeta_homologada VARCHAR(255),
    pais_homologado VARCHAR(100),
    aplica_filtro_pais BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS categoria_manual (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prefijo VARCHAR(100) UNIQUE,
    categoria_homologado VARCHAR(100)
);

-- Data for Local DB
-- 22 Scenarios for pais_homologado
DELETE FROM pais_homologado;
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('garantia,oficina', 'colomb,co', 'Manuales Garantía', 'Colombia');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('user,usuario', 'mex,mx', 'Manuales de Usuario', 'México');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('tech,tecnico', 'braz,bras,br', 'Manuales Técnicos y Mantenimiento', 'Brasil');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('instal,setup', 'arg', 'Guías de Instalación', 'Argentina');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('seguridad,safe', 'chile,cl', 'Manuales de Seguridad', 'Chile');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('calidad,qa', 'peru,pe', 'Certificados de Calidad', 'Perú');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('garant', 'mex,mx', 'Manuales Garantía', 'México');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('uso,manual', 'colomb,co', 'Manuales de Usuario', 'Colombia');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('mantenimiento,maint', 'arg', 'Manuales Técnicos y Mantenimiento', 'Argentina');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('guia,guide', 'braz,bras,br', 'Guías de Instalación', 'Brasil');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('prevencion', 'chile,cl', 'Manuales de Seguridad', 'Chile');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('certificado,cert', 'peru,pe', 'Certificados de Calidad', 'Perú');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('soporte', 'colomb,co', 'Manuales Técnicos y Mantenimiento', 'Colombia');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('operacion,ops', 'mex,mx', 'Manuales de Usuario', 'México');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('industrial', 'braz,bras,br', 'Manuales de Seguridad', 'Brasil');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('procesos', 'arg', 'Certificados de Calidad', 'Argentina');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('directrices', 'chile,cl', 'Manuales de Usuario', 'Chile');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('normas,rules', 'peru,pe', 'Manuales de Seguridad', 'Perú');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('fabrica,factory', 'colomb,co', 'Manuales Técnicos y Mantenimiento', 'Colombia');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('comercial', 'mex,mx', 'Manuales de Usuario', 'México');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('cliente', 'braz,bras,br', 'Manuales de Usuario', 'Brasil');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado) VALUES ('auditoria', 'arg', 'Certificados de Calidad', 'Argentina');
INSERT INTO pais_homologado (carpeta_origen, pais_origen, carpeta_homologada, pais_homologado, aplica_filtro_pais) VALUES ('*', '*', 'Otros / No Catalogado', 'Internacional / Sin Asignar', FALSE);

-- Scenarios for categoria_manual
DELETE FROM categoria_manual;
INSERT INTO categoria_manual (prefijo, categoria_homologado) VALUES ('SC-', 'electrodomestico');
INSERT INTO categoria_manual (prefijo, categoria_homologado) VALUES ('DOC-', 'ropa');
INSERT INTO categoria_manual (prefijo, categoria_homologado) VALUES ('TXT-', 'aseo');
INSERT INTO categoria_manual (prefijo, categoria_homologado) VALUES ('ZIP-', 'zapatos');
