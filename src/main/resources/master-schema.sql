-- Cleanup previous seeds
DELETE FROM productos_maestros;

-- SUCCESS SCENARIOS
INSERT INTO productos_maestros (id_producto, nombre, estado) VALUES ('SC-SUCCESS-01', 'Standard PDF Product', 'ACTIVE');
INSERT INTO productos_maestros (id_producto, nombre, estado) VALUES ('SC-SUCCESS-02', 'Large Valid PDF', 'ACTIVE');
INSERT INTO productos_maestros (id_producto, nombre, estado) VALUES ('SC-SUCCESS-03', 'Valid DOCX Product', 'ACTIVE');
INSERT INTO productos_maestros (id_producto, nombre, estado) VALUES ('SC-SUCCESS-ZIP', 'Valid ZIP with 2 files', 'ACTIVE');

-- RETRY SCENARIOS (Transient Errors)
INSERT INTO productos_maestros (id_producto, nombre, estado) VALUES ('SC-RETRY-TIMEOUT', 'Forced Timeout Scenario', 'ACTIVE');
INSERT INTO productos_maestros (id_producto, nombre, estado) VALUES ('SC-RETRY-500', 'Transient 500 Error', 'ACTIVE');
INSERT INTO productos_maestros (id_producto, nombre, estado) VALUES ('SC-RETRY-429', 'Transient 429 Rate Limit', 'ACTIVE');

-- BUSINESS RULE SCENARIOS (Final Failures)
INSERT INTO productos_maestros (id_producto, nombre, estado) VALUES ('SC-FAIL-SIZE', 'File Too Large (>10MB)', 'ACTIVE');
INSERT INTO productos_maestros (id_producto, nombre, estado) VALUES ('SC-FAIL-EXT', 'Forbidden Extension (.exe)', 'ACTIVE');
INSERT INTO productos_maestros (id_producto, nombre, estado) VALUES ('SC-FAIL-ZIP-EMPTY', 'Empty ZIP Container', 'ACTIVE');

-- TECHNICAL ERROR SCENARIOS (External API)
INSERT INTO productos_maestros (id_producto, nombre, estado) VALUES ('SC-FAIL-SOAP-FAULT', 'SOAP Fault Response', 'ACTIVE');
INSERT INTO productos_maestros (id_producto, nombre, estado) VALUES ('SC-FAIL-MALFORMED', 'Malformed XML Response', 'ACTIVE');
INSERT INTO productos_maestros (id_producto, nombre, estado) VALUES ('SC-FAIL-EMPTY', 'Empty Response Body', 'ACTIVE');
