CREATE TABLE IF NOT EXISTS historico_documentos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_producto VARCHAR(255) NOT NULL,
    id_documento VARCHAR(500) NOT NULL,
    nombre_archivo VARCHAR(500) NOT NULL,
    nombre_comprimido VARCHAR(500),
    estado VARCHAR(20) NOT NULL DEFAULT 'FAILURE',
    codigo_error VARCHAR(100),
    razon_fallo VARCHAR(2000),
    numero_intentos INT NOT NULL DEFAULT 1,
    fecha_envio TIMESTAMP,
    fecha_fallo TIMESTAMP,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS productos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_producto VARCHAR(255) NOT NULL,
    nombre VARCHAR(500) NOT NULL,
    fecha_carga TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    mensaje_error VARCHAR(2000)
);
