package com.example.fileprocessor.infrastructure.helpers.soap.metadata;

import com.example.fileprocessor.domain.entity.FileUploadRequest;

/**
 * Strategy for building the XML metadata block inside the SOAP envelope.
 * Each use case implements its own metadata content.
 */
public interface MetadataStrategy {
    /**
     * Generates the XML content that goes inside {@code <metaData>...</metaData>}.
     *
     * @param request the file upload request containing context-specific fields
     * @return raw XML fragment for the metadata block
     */
    String buildMetadataBlock(FileUploadRequest request);
}
