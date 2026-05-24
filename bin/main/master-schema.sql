-- Schema for Master DB
CREATE TABLE IF NOT EXISTS productos_maestros (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_producto VARCHAR(100) UNIQUE,
    nombre VARCHAR(255),
    estado VARCHAR(50),
    carpeta_origen VARCHAR(255),
    pais_origen VARCHAR(100),
    fecha_cargue TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Cleanup
DELETE FROM productos_maestros;

-- 1-5: SUCCESS SCENARIOS
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-01', 'Standard PDF', 'ACTIVE', '/mnt/shared/docs/standard', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-02', 'Large Valid PDF (9MB)', 'ACTIVE', '/mnt/shared/docs/large', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-03', 'DOCX Document', 'ACTIVE', '/mnt/shared/docs/word', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-04', 'TXT Document', 'ACTIVE', '/mnt/shared/docs/text', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-05', 'ZIP with 3 Valid PDFs', 'ACTIVE', '/mnt/shared/docs/zip', 'CO');

-- 6-10: RETRY SCENARIOS (Transient Errors)
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-RT-01', 'Timeout (Forced)', 'ACTIVE', '/mnt/network/retry', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-RT-02', 'Transient 500 Error', 'ACTIVE', '/mnt/network/retry', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-RT-03', 'Transient 503 Service Unavailable', 'ACTIVE', '/mnt/network/retry', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-RT-04', 'Transient 429 Rate Limit', 'ACTIVE', '/mnt/network/retry', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-RT-05', 'Transient 504 Gateway Timeout', 'ACTIVE', '/mnt/network/retry', 'AR');

-- 11-16: BUSINESS RULE SCENARIOS (Final Failures)
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-01', 'File Too Large (20MB)', 'ACTIVE', '/var/data/invalid', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-02', 'Invalid Extension (.exe)', 'ACTIVE', '/var/data/invalid', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-03', 'Invalid Extension (.sh)', 'ACTIVE', '/var/data/invalid', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-04', 'Filename Pattern Mismatch', 'ACTIVE', '/var/data/invalid', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-05', 'Empty ZIP Container', 'ACTIVE', '/var/data/invalid', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-06', 'ZIP with non-PDF files', 'ACTIVE', '/var/data/invalid', 'CO');

-- 17-23: TECHNICAL ERROR SCENARIOS (External API)
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-01', 'SOAP Fault Response', 'ACTIVE', '/opt/tech/errors', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-02', 'Malformed XML Response', 'ACTIVE', '/opt/tech/errors', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-03', 'Empty Response Body', 'ACTIVE', '/opt/tech/errors', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-04', 'HTTP 401 Unauthorized', 'ACTIVE', '/opt/tech/errors', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-05', 'HTTP 403 Forbidden', 'ACTIVE', '/opt/tech/errors', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-06', 'HTTP 400 Bad Request', 'ACTIVE', '/opt/tech/errors', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-07', 'Corrupted Base64 Content', 'ACTIVE', '/opt/tech/errors', 'AR');
