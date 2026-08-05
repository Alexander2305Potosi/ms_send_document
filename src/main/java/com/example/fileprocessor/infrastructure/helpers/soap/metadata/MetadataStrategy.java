package com.example.fileprocessor.infrastructure.helpers.soap.metadata;

import com.example.fileprocessor.domain.entity.ProductUploadRequest;

/**
 * Estrategia para construir el bloque XML de metadatos dentro del envelope SOAP.
 * Cada caso de uso implementa su propio contenido de metadatos.
 */
public interface MetadataStrategy {
    /**
     * Genera el contenido XML que va dentro de {@code <metaData>...</metaData>}.
     *
     * @param request la solicitud de carga con los campos específicos del caso de uso
     * @return fragmento XML crudo para el bloque de metadatos
     */
    String buildMetadataBlock(ProductUploadRequest request);
}
