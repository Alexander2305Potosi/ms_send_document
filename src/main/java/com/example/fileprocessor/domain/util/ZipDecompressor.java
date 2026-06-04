package com.example.fileprocessor.domain.util;

import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class for decompressing ZIP archives into individual DocumentHistoryDTO instances.
 */
public final class ZipDecompressor {

    private ZipDecompressor() {}

    public static Flux<DocumentHistoryDTO> decompress(DocumentHistoryDTO zipHistory) {
        if (!Boolean.TRUE.equals(zipHistory.getIsZip()) || zipHistory.getContent() == null) {
            return Flux.just(zipHistory);
        }

        try {
            java.io.File tempFile = java.io.File.createTempFile("decompress-", ".zip");
            try {
                java.nio.file.Files.write(tempFile.toPath(), zipHistory.getContent());

                java.util.List<DocumentHistoryDTO> entries = new java.util.ArrayList<>();
                java.util.zip.ZipFile zipFile;
                try {
                    zipFile = new java.util.zip.ZipFile(tempFile, java.nio.charset.StandardCharsets.UTF_8);
                } catch (java.util.zip.ZipException e) {
                    zipFile = new java.util.zip.ZipFile(tempFile, java.nio.charset.StandardCharsets.ISO_8859_1);
                }
                try {
                    java.util.Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
                    while (enumeration.hasMoreElements()) {
                        ZipEntry entry = enumeration.nextElement();
                        if (!entry.isDirectory() && entry.getName() != null && !entry.getName().isBlank()) {
                            try (java.io.InputStream is = zipFile.getInputStream(entry)) {
                                byte[] decompressed = is.readAllBytes();
                                entries.add(buildHistoryEntry(entry.getName(), decompressed, zipHistory));
                            }
                        }
                    }
                } finally {
                    zipFile.close();
                }
                return Flux.fromIterable(entries);
            } finally {
                java.nio.file.Files.deleteIfExists(tempFile.toPath());
            }
        } catch (IOException e) {
            return Flux.error(new ProcessingException(
                "Failed to decompress ZIP '" + zipHistory.getFilename() + "': " + e.getMessage(),
                ProcessingResultCodes.DECOMPRESSION_ERROR.name(),
                e));
        }
    }

    private static DocumentHistoryDTO buildHistoryEntry(String filename, byte[] content, DocumentHistoryDTO original) {
        return original.toBuilder()
            .businessDocumentId(original.getBusinessDocumentId() + "/" + filename)
            .filename(filename)
            .contentType(MimeTypeUtil.getMimeType(filename))
            .size((long) content.length)
            .isZip(false)
            .content(content)
            .build();
    }
}
