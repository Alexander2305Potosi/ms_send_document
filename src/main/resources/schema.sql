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

CREATE TABLE IF NOT EXISTS categoria_manual (
    id BIGSERIAL PRIMARY KEY,
    categoria VARCHAR(255) NOT NULL UNIQUE,
    descripcion_manual VARCHAR(500) NOT NULL,
    fecha_vigencia DATE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cat_categoria_vigencia ON categoria_manual (categoria, fecha_vigencia);

CREATE TABLE IF NOT EXISTS pais_homologado (
    id BIGSERIAL PRIMARY KEY,
    pais VARCHAR(255) NOT NULL UNIQUE,
    pais_homologado VARCHAR(255) NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
