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
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return MediaTypeConstants.APPLICATION_PDF;
        } else if (lower.endsWith(".docx")) {
            return MediaTypeConstants.APPLICATION_WORD;
        } else if (lower.endsWith(".txt")) {
            return MediaTypeConstants.TEXT_PLAIN;
        } else if (lower.endsWith(".xml")) {
            return MediaTypeConstants.APPLICATION_XML;
        } else if (lower.endsWith(".json")) {
            return MediaTypeConstants.APPLICATION_JSON;
        }
        return MediaTypeConstants.APPLICATION_OCTET_STREAM;
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