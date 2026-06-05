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
    sucursal VARCHAR(255),
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
    orden INT NOT NULL,
    condicion_jsonb TEXT,
    carpeta_homologada VARCHAR(255),
    pais_homologado VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS categoria_manual (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prefijo VARCHAR(100) UNIQUE,
    categoria_homologado VARCHAR(100)
);

-- Data for Local DB
-- 23 Scenarios for pais_homologado
DELETE FROM pais_homologado;
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (10, '{"originFolder": {"$containsAny": ["garantia", "oficina"]}, "originCountry": {"$containsAny": ["colomb", "co"]}}', 'Manuales Garantía', 'Colombia');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (20, '{"originFolder": {"$containsAny": ["user", "usuario"]}, "originCountry": {"$containsAny": ["mex", "mx"]}}', 'Manuales de Usuario', 'México');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (30, '{"originFolder": {"$containsAny": ["tech", "tecnico"]}, "originCountry": {"$containsAny": ["braz", "bras", "br"]}}', 'Manuales Técnicos y Mantenimiento', 'Brasil');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (40, '{"originFolder": {"$containsAny": ["instal", "setup"]}, "originCountry": {"$containsAny": ["arg"]}}', 'Guías de Instalación', 'Argentina');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (50, '{"originFolder": {"$containsAny": ["seguridad", "safe"]}, "originCountry": {"$containsAny": ["chile", "cl"]}}', 'Manuales de Seguridad', 'Chile');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (60, '{"originFolder": {"$containsAny": ["calidad", "qa"]}, "originCountry": {"$containsAny": ["peru", "pe"]}}', 'Certificados de Calidad', 'Perú');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (70, '{"originFolder": {"$containsAny": ["garant"]}, "originCountry": {"$containsAny": ["mex", "mx"]}}', 'Manuales Garantía', 'México');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (80, '{"originFolder": {"$containsAny": ["uso", "manual"]}, "originCountry": {"$containsAny": ["colomb", "co"]}}', 'Manuales de Usuario', 'Colombia');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (90, '{"originFolder": {"$containsAny": ["mantenimiento", "maint"]}, "originCountry": {"$containsAny": ["arg"]}}', 'Manuales Técnicos y Mantenimiento', 'Argentina');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (100, '{"originFolder": {"$containsAny": ["guia", "guide"]}, "originCountry": {"$containsAny": ["braz", "bras", "br"]}}', 'Guías de Instalación', 'Brasil');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (110, '{"originFolder": {"$containsAny": ["prevencion"]}, "originCountry": {"$containsAny": ["chile", "cl"]}}', 'Manuales de Seguridad', 'Chile');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (120, '{"originFolder": {"$containsAny": ["certificado", "cert"]}, "originCountry": {"$containsAny": ["peru", "pe"]}}', 'Certificados de Calidad', 'Perú');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (130, '{"originFolder": {"$containsAny": ["soporte"]}, "originCountry": {"$containsAny": ["colomb", "co"]}}', 'Manuales Técnicos y Mantenimiento', 'Colombia');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (140, '{"originFolder": {"$containsAny": ["operacion", "ops"]}, "originCountry": {"$containsAny": ["mex", "mx"]}}', 'Manuales de Usuario', 'México');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (150, '{"originFolder": {"$containsAny": ["industrial"]}, "originCountry": {"$containsAny": ["braz", "bras", "br"]}}', 'Manuales de Seguridad', 'Brasil');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (160, '{"originFolder": {"$containsAny": ["procesos"]}, "originCountry": {"$containsAny": ["arg"]}}', 'Certificados de Calidad', 'Argentina');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (170, '{"originFolder": {"$containsAny": ["directrices"]}, "originCountry": {"$containsAny": ["chile", "cl"]}}', 'Manuales de Usuario', 'Chile');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (180, '{"originFolder": {"$containsAny": ["normas", "rules"]}, "originCountry": {"$containsAny": ["peru", "pe"]}}', 'Manuales de Seguridad', 'Perú');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (190, '{"originFolder": {"$containsAny": ["fabrica", "factory"]}, "originCountry": {"$containsAny": ["colomb", "co"]}}', 'Manuales Técnicos y Mantenimiento', 'Colombia');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (200, '{"originFolder": {"$containsAny": ["comercial"]}, "originCountry": {"$containsAny": ["mex", "mx"]}}', 'Manuales de Usuario', 'México');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (210, '{"originFolder": {"$containsAny": ["cliente"]}, "originCountry": {"$containsAny": ["braz", "bras", "br"]}}', 'Manuales de Usuario', 'Brasil');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (220, '{"originFolder": {"$containsAny": ["auditoria"]}, "originCountry": {"$containsAny": ["arg"]}}', 'Certificados de Calidad', 'Argentina');
INSERT INTO pais_homologado (orden, condicion_jsonb, carpeta_homologada, pais_homologado) VALUES (9999, '{}', 'Otros / No Catalogado', 'Internacional / Sin Asignar');

-- Scenarios for categoria_manual
DELETE FROM categoria_manual;
INSERT INTO categoria_manual (prefijo, categoria_homologado) VALUES ('SC-', 'electrodomestico');
INSERT INTO categoria_manual (prefijo, categoria_homologado) VALUES ('DOC-', 'ropa');
INSERT INTO categoria_manual (prefijo, categoria_homologado) VALUES ('TXT-', 'aseo');
INSERT INTO categoria_manual (prefijo, categoria_homologado) VALUES ('ZIP-', 'zapatos');

-- Schema & Data for custom schema branch query (otro_esquema.tabla_maestra)
CREATE SCHEMA IF NOT EXISTS otro_esquema;
CREATE TABLE IF NOT EXISTS otro_esquema.tabla_maestra (
    id_producto VARCHAR(100) PRIMARY KEY,
    sucursal VARCHAR(255)
);

DELETE FROM otro_esquema.tabla_maestra;
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-OK-01', 'Sucursal Bogota');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-OK-02', 'Sucursal Medellin');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-OK-03', 'Sucursal Cali');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-OK-04', 'Sucursal Barranquilla');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-OK-05', 'Sucursal Cartagena');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-OK-06', 'Sucursal Mexico');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-OK-07', 'Sucursal Sao Paulo');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-OK-08', 'Sucursal Buenos Aires');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-OK-09', 'Sucursal Santiago');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-OK-10', 'Sucursal Lima');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-OK-11', 'Sucursal Bogota');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-OK-12', 'Sucursal Medellin');

INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-RT-01', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-RT-02', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-RT-03', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-RT-04', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-RT-05', 'Sucursal Central');

INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-BR-01', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-BR-02', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-BR-03', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-BR-04', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-BR-05', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-BR-06', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-BR-07', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-BR-08', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-BR-09', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-BR-10', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-BR-11', 'Sucursal Central');
-- SC-BR-12 is intentionally omitted (no sucursal in tabla_maestra)

INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-TE-01', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-TE-02', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-TE-03', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-TE-04', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-TE-05', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-TE-06', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-TE-07', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-TE-08', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-TE-09', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-TE-10', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-TE-11', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-TE-12', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-TE-13', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-TE-14', 'Sucursal Central');

INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-OK-13', 'Sucursal Mexico');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-OK-14', 'Sucursal Bogota');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-RT-06', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-TE-15', 'Sucursal Central');
INSERT INTO otro_esquema.tabla_maestra (id_producto, sucursal) VALUES ('SC-TE-16', 'Sucursal Central');


