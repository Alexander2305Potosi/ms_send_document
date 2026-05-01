CREATE TABLE IF NOT EXISTS historico_documentos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre_producto VARCHAR NOT NULL,
    nombre_documento VARCHAR NOT NULL,
    nombre_archivo VARCHAR NOT NULL,
    nombre_comprimido VARCHAR,
    estado VARCHAR NOT NULL,
    codigo_error VARCHAR,
    razon_fallo VARCHAR,
    numero_intentos INT NOT NULL DEFAULT 1,
    fecha_envio TIMESTAMP,
    fecha_fallo TIMESTAMP,
    fecha_creacion TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS productos_pendientes (
    nombre_producto VARCHAR PRIMARY KEY,
    nombre VARCHAR,
    fecha_carga TIMESTAMP,
    estado VARCHAR,
    mensaje_error VARCHAR,
    fecha_creacion TIMESTAMP,
    fecha_actualizacion TIMESTAMP
);
