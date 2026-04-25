package com.example.fileprocessor.domain.entity;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Base64;

/**
 * SOAP request representation for sending files to external service.
 */
@Getter
@Builder
public class SoapRequest {
    private final String fileContentBase64;
    private final String filename;
    private final String contentType;
    private final long fileSize;
    private final String traceId;
    private final Instant timestamp;
    private final String parentFolder;
    private final String childFolder;

    public static SoapRequest fromFileData(FileData fileData, String parentFolder, String childFolder) {
        return SoapRequest.builder()
            .fileContentBase64(Base64.getEncoder().encodeToString(fileData.getContent()))
            .filename(fileData.getFilename())
            .contentType(fileData.getContentType())
            .fileSize(fileData.getSize())
            .traceId(fileData.getTraceId())
            .timestamp(Instant.now())
            .parentFolder(parentFolder)
            .childFolder(childFolder)
            .build();
    }
}