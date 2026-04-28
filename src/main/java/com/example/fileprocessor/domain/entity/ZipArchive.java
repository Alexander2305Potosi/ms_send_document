package com.example.fileprocessor.domain.entity;

import com.example.fileprocessor.domain.util.MediaTypeConstants;
import lombok.Builder;
import lombok.Getter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Represents a ZIP archive containing documents to be processed.
 */
@Getter
@Builder
public class ZipArchive {
    private static final java.util.Map<String, String> EXT_TO_MIME = java.util.Map.of(
        ".pdf", MediaTypeConstants.APPLICATION_PDF,
        ".docx", MediaTypeConstants.APPLICATION_WORD,
        ".txt", MediaTypeConstants.TEXT_PLAIN,
        ".xml", MediaTypeConstants.APPLICATION_XML,
        ".json", MediaTypeConstants.APPLICATION_JSON
    );

    private final byte[] zipContent;
    private final String originalFilename;

    /**
     * Extracts all documents from the ZIP archive.
     *
     * @return List of ExtractedDocument containing file data and metadata
     * @throws IOException if the ZIP content is invalid or cannot be read
     */
    public List<ExtractedDocument> extractDocuments() throws IOException {
        List<ExtractedDocument> documents = new ArrayList<>();

        try (InputStream is = new ByteArrayInputStream(zipContent);
             ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
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

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = zis.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                byte[] content = baos.toByteArray();

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
            .map(java.util.Map.Entry::getValue)
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