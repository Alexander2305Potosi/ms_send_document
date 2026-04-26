package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

/**
 * SOAP request representation for sending files to external service.
 * FIX #5: Domain NO hace encoding Base64 - eso es responsabilidad de infraestructura.
 * El domain solo transporta los datos puros (byte[]).
 */
@Getter
@Builder
public class SoapRequest {
    private final String documentId;  // Added for tracking
    private final byte[] fileContent;  // Raw bytes, NO Base64 en domain
    private final String filename;
    private final String contentType;
    private final long fileSize;
    private final String traceId;
    private final String parentFolder;
    private final String childFolder;

    /**
     * Creates SoapRequest from FileData.
     * FIX #5: No hace encoding aquí - lo hace el adapter en infraestructura.
     */
    public static SoapRequest fromFileData(FileData fileData, String parentFolder, String childFolder) {
        return SoapRequest.builder()
            .fileContent(fileData.getContent())  // Raw bytes, no encoding
            .filename(fileData.getFilename())
            .contentType(fileData.getContentType())
            .fileSize(fileData.getSize())
            .traceId(fileData.getTraceId())
            .parentFolder(parentFolder)
            .childFolder(childFolder)
            .build();
    }
}