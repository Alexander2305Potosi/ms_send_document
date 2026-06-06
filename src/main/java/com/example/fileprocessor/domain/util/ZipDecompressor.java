package com.example.fileprocessor.domain.util;

import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Utility class for decompressing ZIP archives into individual DocumentHistoryDTO instances.
 */
public final class ZipDecompressor {

    private ZipDecompressor() {}

    public static Flux<DocumentHistoryDTO> decompress(DocumentHistoryDTO zipHistory, String tempDirPath) {
        if (!Boolean.TRUE.equals(zipHistory.getIsZip()) || zipHistory.getContent() == null) {
            return Flux.just(zipHistory);
        }

        try {
            java.nio.file.Path tempFilePath;
            if (tempDirPath != null && !tempDirPath.trim().isEmpty()) {
                java.nio.file.Path dirPath = java.nio.file.Paths.get(tempDirPath);
                if (!java.nio.file.Files.exists(dirPath)) {
                    java.nio.file.Files.createDirectories(dirPath);
                }
                tempFilePath = java.nio.file.Files.createTempFile(dirPath, "decompress-", ".zip");
            } else {
                tempFilePath = java.nio.file.Files.createTempFile("decompress-", ".zip");
            }
            File tempFile = tempFilePath.toFile();
            try {
                Files.write(tempFile.toPath(), zipHistory.getContent());

                List<DocumentHistoryDTO> entries = new ArrayList<>();
                ZipFile zipFile;
                try {
                    zipFile = new ZipFile(tempFile, java.nio.charset.StandardCharsets.UTF_8);
                } catch (ZipException e) {
                    zipFile = new ZipFile(tempFile, java.nio.charset.StandardCharsets.ISO_8859_1);
                }
                try {
                    final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MB limit per file
                    int totalEntries = 0;
                    final int MAX_ENTRIES = 1000;

                    Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
                    while (enumeration.hasMoreElements()) {
                        ZipEntry entry = enumeration.nextElement();
                        totalEntries++;
                        
                        if (totalEntries > MAX_ENTRIES) {
                            throw new ProcessingException("Too many entries in ZIP (Zip Bomb protection)", ProcessingResultCodes.DECOMPRESSION_ERROR.name());
                        }

                        if (!entry.isDirectory() && entry.getName() != null && !entry.getName().isBlank()) {
                            String entryName = entry.getName();
                            
                            // 1. Prevenir Zip Slip (Path Traversal)
                            if (entryName.contains("..")) {
                                throw new ProcessingException("Zip Slip vulnerability detected: " + entryName, ProcessingResultCodes.DECOMPRESSION_ERROR.name());
                            }

                            try (java.io.InputStream is = zipFile.getInputStream(entry)) {
                                // 2. Prevenir Zip Bomb (Limitando la lectura a MAX_FILE_SIZE + 1)
                                byte[] decompressed = is.readNBytes((int) MAX_FILE_SIZE + 1);
                                if (decompressed.length > MAX_FILE_SIZE) {
                                    throw new ProcessingException("ZIP entry exceeds maximum allowed size (Zip Bomb protection)", ProcessingResultCodes.DECOMPRESSION_ERROR.name());
                                }
                                entries.add(buildHistoryEntry(entryName, decompressed, zipHistory));
                            }
                        }
                    }
                } finally {
                    zipFile.close();
                }
                return Flux.fromIterable(entries);
            } finally {
                Files.deleteIfExists(tempFile.toPath());
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
