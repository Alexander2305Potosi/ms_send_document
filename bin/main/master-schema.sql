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

-- 1-12: SUCCESS SCENARIOS
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-01', 'Standard PDF', 'ACTIVE', '/mnt/shared/docs/standard', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-02', 'Large Valid PDF (9MB)', 'ACTIVE', '/mnt/shared/docs/large', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-03', 'DOCX Document', 'ACTIVE', '/mnt/shared/docs/word', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-04', 'TXT Document', 'ACTIVE', '/mnt/shared/docs/text', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-05', 'ZIP with 3 Valid PDFs', 'ACTIVE', '/mnt/shared/docs/zip', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-06', 'Mexico User Manual', 'ACTIVE', '/mnt/shared/docs/usuario', 'MX');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-07', 'Brazil Tech Manual', 'ACTIVE', '/mnt/shared/docs/tecnico', 'BR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-08', 'Argentina Setup Guide', 'ACTIVE', '/mnt/shared/docs/setup', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-09', 'Chile Security Manual', 'ACTIVE', '/mnt/shared/docs/seguridad', 'CL');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-10', 'Peru Quality Cert', 'ACTIVE', '/mnt/shared/docs/calidad', 'PE');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-11', 'ZIP with Multi-extension (PDF, DOCX, TXT)', 'ACTIVE', '/mnt/shared/docs/zip', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-12', 'ZIP with Nested Folders', 'ACTIVE', '/mnt/shared/docs/zip', 'CO');

-- RETRY SCENARIOS (Transient Errors)
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-RT-01', 'Timeout (Forced)', 'ACTIVE', '/mnt/network/retry', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-RT-02', 'Transient 500 Error', 'ACTIVE', '/mnt/network/retry', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-RT-03', 'Transient 503 Service Unavailable', 'ACTIVE', '/mnt/network/retry', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-RT-04', 'Transient 429 Rate Limit', 'ACTIVE', '/mnt/network/retry', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-RT-05', 'Transient 504 Gateway Timeout', 'ACTIVE', '/mnt/network/retry', 'AR');

-- BUSINESS RULE SCENARIOS (Final Failures)
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-01', 'File Too Large (20MB)', 'ACTIVE', '/var/data/invalid', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-02', 'Invalid Extension (.exe)', 'ACTIVE', '/var/data/invalid', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-03', 'Invalid Extension (.sh)', 'ACTIVE', '/var/data/invalid', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-04', 'Filename Pattern Mismatch', 'ACTIVE', '/var/data/invalid', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-05', 'Empty ZIP Container', 'ACTIVE', '/var/data/invalid', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-06', 'ZIP with non-PDF files', 'ACTIVE', '/var/data/invalid', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-07', 'Duplicate Document Sync', 'ACTIVE', '/var/data/invalid', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-08', 'ZIP with Oversized Inner File (>10MB)', 'ACTIVE', '/var/data/invalid', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-09', 'ZIP with Unsupported Extension (.exe)', 'ACTIVE', '/var/data/invalid', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-10', 'ZIP with Empty Inner File', 'ACTIVE', '/var/data/invalid', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-11', 'Malformed ZIP Container', 'ACTIVE', '/var/data/invalid', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-BR-12', 'Product with No Sucursal in DB', 'ACTIVE', '/var/data/invalid', 'CO');

-- TECHNICAL ERROR SCENARIOS (External API)
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-01', 'SOAP Fault Response', 'ACTIVE', '/opt/tech/errors', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-02', 'Malformed XML Response', 'ACTIVE', '/opt/tech/errors', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-03', 'Empty Response Body', 'ACTIVE', '/opt/tech/errors', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-04', 'HTTP 401 Unauthorized', 'ACTIVE', '/opt/tech/errors', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-05', 'HTTP 403 Forbidden', 'ACTIVE', '/opt/tech/errors', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-06', 'HTTP 400 Bad Request', 'ACTIVE', '/opt/tech/errors', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-07', 'Corrupted Base64 Content', 'ACTIVE', '/opt/tech/errors', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-08', 'Persistent HTTP 500 SOAP Error', 'ACTIVE', '/opt/tech/errors', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-09', 'Persistent HTTP 503 SOAP Error', 'ACTIVE', '/opt/tech/errors', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-10', 'Persistent HTTP 504 SOAP Error', 'ACTIVE', '/opt/tech/errors', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-11', 'Product REST 500 on Doc List Sync', 'ACTIVE', '/opt/tech/errors', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-12', 'Product REST 404 on Doc List Sync', 'ACTIVE', '/opt/tech/errors', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-13', 'Product REST 500 on Doc Download', 'ACTIVE', '/opt/tech/errors', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-14', 'Product REST 404 on Doc Download', 'ACTIVE', '/opt/tech/errors', 'CO');

-- Additional scenarios for E2E validation
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-13', 'Mexico Word Doc', 'ACTIVE', '/mnt/shared/docs/usuario', 'MX');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-OK-14', 'Colombia Valid ZIP', 'ACTIVE', '/mnt/shared/docs/zip', 'CO');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-RT-06', 'Transient 503 Retry (Success on 2nd)', 'ACTIVE', '/mnt/network/retry', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-15', 'Persistent Malformed XML SOAP Response', 'ACTIVE', '/opt/tech/errors', 'AR');
INSERT INTO productos_maestros (id_producto, nombre, estado, carpeta_origen, pais_origen) VALUES ('SC-TE-16', 'Persistent SOAP Connection Timeout', 'ACTIVE', '/opt/tech/errors', 'CO');
