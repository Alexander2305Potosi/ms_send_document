package com.example.fileprocessor.domain.entity;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Clase base abstracta para todas las solicitudes de carga de archivos.
 * Contiene los campos comunes a todos los casos de uso.
 * Cada caso de uso debe extender esta clase para agregar sus campos específicos.
 *
 * @see ProductUploadRequest  – para el caso de uso SOAP/S3 de productos
 * @see AnimalUploadRequest   – para el caso de uso de animales
 */
@Getter
@SuperBuilder(toBuilder = true)
public abstract class FileUploadRequest {
    private final String documentId;
    private final byte[] content;
    private final String filename;
    private final String contentType;
    private final long fileSize;
    private final String originFolder;
    private final String categoriaDocument;
    private final String homologationFolder;
    private final String homologationCountry;
    private final Long docId;
    private final String useCase;
}
