package com.example.fileprocessor.domain.entity;

/**
 * Request object for file upload operations.
 */
public record FileUploadRequest(
    String documentId,
    byte[] content,
    String filename,
    String contentType,
    long fileSize,
    String parentFolder,
    String childFolder,
    String origin
) {
    public static FileUploadRequest of(String documentId, byte[] content, String filename,
            String contentType, long fileSize, String parentFolder, String childFolder, String origin) {
        return new FileUploadRequest(documentId, content, filename, contentType, fileSize, parentFolder, childFolder, origin);
    }

    public static FileUploadRequest of(String documentId, byte[] content, String filename,
            String contentType, long fileSize, String parentFolder, String childFolder) {
        return new FileUploadRequest(documentId, content, filename, contentType, fileSize, parentFolder, childFolder, null);
    }
}
