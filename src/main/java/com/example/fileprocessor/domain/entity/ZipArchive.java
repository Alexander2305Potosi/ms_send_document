package com.example.fileprocessor.domain.entity;

import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.domain.util.MediaTypeConstants;
import lombok.Builder;
import lombok.Getter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Represents a ZIP archive containing documents to be processed.
 * Includes protection against ZIP bombs with configurable size limits.
 */
@Getter
@Builder
public class ZipArchive {
    private static final Map<String, String> EXT_TO_MIME = Map.of(
        ".pdf", MediaTypeConstants.APPLICATION_PDF,
        ".docx", MediaTypeConstants.APPLICATION_WORD,
        ".txt", MediaTypeConstants.TEXT_PLAIN,
        ".xml", MediaTypeConstants.APPLICATION_XML,
        ".json", MediaTypeConstants.APPLICATION_JSON,
        ".png", "image/png",
        ".jpg", "image/jpeg",
        ".gif", "image/gif",
        ".html", "text/html",
        ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    // Default limits to prevent ZIP bombs
    private static final int DEFAULT_MAX_ENTRIES = 1000;
    private static final long DEFAULT_MAX_UNCOMPRESSED_SIZE = 100 * 1024 * 1024; // 100MB

    private final byte[] zipContent;
    private final String originalFilename;

    @Builder.Default
    private final int maxEntries = DEFAULT_MAX_ENTRIES;

    @Builder.Default
    private final long maxUncompressedSize = DEFAULT_MAX_UNCOMPRESSED_SIZE;

    /**
     * Extracts all documents from the ZIP archive with size limits to prevent ZIP bombs.
     *
     * @return List of ExtractedDocument containing file data and metadata
     * @throws IOException if the ZIP content is invalid or cannot be read
     * @throws FileValidationException if ZIP exceeds size limits
     */
    public List<ExtractedDocument> extractDocuments() throws IOException {
        List<ExtractedDocument> documents = new ArrayList<>();
        long totalUncompressedSize = 0;
        int entryCount = 0;

        try (InputStream is = new ByteArrayInputStream(zipContent);
             ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Check entry count limit
                entryCount++;
                if (entryCount > maxEntries) {
                    throw new FileValidationException(
                        "ZIP archive exceeds maximum entry count: " + entryCount + " > " + maxEntries,
                        ProcessingResultCodes.ZIP_EXTRACTION_FAILED);
                }

                if (entry.isDirectory() || entry.getName().startsWith("_")) {
                    zis.closeEntry();
                    continue;
                }

                String entryName = entry.getName();
                int lastSlash = Math.max(entryName.lastIndexOf('/'), entryName.lastIndexOf('\\'));
                String filename = lastSlash >= 0 ? entryName.substring(lastSlash + 1) : entryName;

                if (filename.isEmpty()) {
                    zis.closeEntry();
                    continue;
                }

                // Check compressed size before reading
                long compressedSize = entry.getCompressedSize();
                if (compressedSize > maxUncompressedSize) {
                    throw new FileValidationException(
                        "ZIP entry exceeds maximum compressed size: " + compressedSize,
                        ProcessingResultCodes.ZIP_EXTRACTION_FAILED);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                long entryUncompressedSize = 0;

                while ((bytesRead = zis.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                    entryUncompressedSize += bytesRead;

                    // Check individual entry size limit
                    if (entryUncompressedSize > maxUncompressedSize) {
                        throw new FileValidationException(
                            "ZIP entry exceeds maximum uncompressed size: " + entryUncompressedSize,
                            ProcessingResultCodes.ZIP_EXTRACTION_FAILED);
                    }

                    // Check total accumulated size limit
                    if (totalUncompressedSize + entryUncompressedSize > maxUncompressedSize) {
                        throw new FileValidationException(
                            "ZIP archive exceeds total uncompressed size limit: " + (totalUncompressedSize + entryUncompressedSize),
                            ProcessingResultCodes.ZIP_EXTRACTION_FAILED);
                    }
                }

                byte[] content = baos.toByteArray();
                totalUncompressedSize += content.length;

                documents.add(ExtractedDocument.builder()
                    .filename(filename)
                    .content(content)
                    .contentType(detectContentType(filename))
                    .size(content.length)
                    .build());

                zis.closeEntry();
            }
        }

        return documents;
    }

    private String detectContentType(String filename) {
        return EXT_TO_MIME.entrySet().stream()
            .filter(entry -> filename.toLowerCase().endsWith(entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(MediaTypeConstants.APPLICATION_OCTET_STREAM);
    }

    @Getter
    @Builder
    public static class ExtractedDocument {
        private final String filename;
        private final byte[] content;
        private final String contentType;
        private final long size;
    }
}