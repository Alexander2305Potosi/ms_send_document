CREATE TABLE IF NOT EXISTS documentos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_documentos_estado ON documentos(estado);
CREATE INDEX IF NOT EXISTS idx_documentos_documento_id ON documentos(id_documento);
CREATE INDEX IF NOT EXISTS idx_documentos_producto_id ON documentos(id_producto);
CREATE INDEX IF NOT EXISTS idx_documentos_caso_uso ON documentos(caso_uso);

CREATE TABLE IF NOT EXISTS historico_documentos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    documento_id BIGINT NOT NULL,
    nombre_archivo VARCHAR(255),
    operacion VARCHAR(50),
    message_id VARCHAR(100),
    resultado VARCHAR(50),
    codigo_error VARCHAR(50),
    mensaje_error TEXT,
    stack_trace TEXT,
    reintentos INT NOT NULL DEFAULT 0,
    fecha_inicio TIMESTAMP,
    fecha_fin TIMESTAMP,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (documento_id) REFERENCES documentos(id)
);

CREATE INDEX IF NOT EXISTS idx_historico_documento_id ON historico_documentos(documento_id);
CREATE INDEX IF NOT EXISTS idx_historico_doc_operacion ON historico_documentos(documento_id, operacion, fecha_creacion);

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
