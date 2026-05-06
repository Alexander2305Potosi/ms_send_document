CREATE TABLE IF NOT EXISTS historico_documentos (
    id_historico_documentos BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_documento VARCHAR(100) NOT NULL,
    id_producto VARCHAR(100) NOT NULL,
    activo BOOLEAN DEFAULT TRUE,
    clave_documento VARCHAR(255),
    nombre VARCHAR(255),
    propietario VARCHAR(255),
    ruta TEXT,
    estado VARCHAR(100) NOT NULL,
    version_contrato VARCHAR(50),
    mensaje_error TEXT,
    es_zip BOOLEAN DEFAULT FALSE,
    nombre_zip_padre VARCHAR(255),
    caso_uso VARCHAR(100),
    resultado VARCHAR(50),
    codigo_error VARCHAR(50),
    reintentos INT NOT NULL DEFAULT 0,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_historico_documento_id ON historico_documentos(id_documento);
CREATE INDEX IF NOT EXISTS idx_historico_estado ON historico_documentos(estado);

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
