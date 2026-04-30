package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;

/**
 * Represents a document ready to be uploaded after validation.
 */
public record DocumentToUpload(
        ProductDocumentToProcess document,
        FileValidator.FolderInfo folderInfo,
        long fileSize,
        boolean skipped
) {
    public String documentId() { return document.getDocumentId(); }
    public byte[] content() { return document.getContent(); }
    public String filename() { return document.getFilename(); }
    public String contentType() { return document.getContentType(); }
    public String origin() { return document.getOrigin(); }
    public String productId() { return document.getProductId(); }
}
