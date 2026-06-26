package com.example.fileprocessor.domain.util;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.DECOMPRESSION_ERROR;

import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Utility class for decompressing ZIP archives into individual DocumentHistoryDTO instances.
 * Restored the previous file-based ZipFile logic, with robust temporary directory path fallback options.
 * Refactored to reduce control statement nesting to at most 3 levels to improve readability and complexity.
 * Adheres to Sonar code quality guidelines (using var, L suffixes on long literals, and class constants).
 */
public final class ZipDecompressor {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50 MB limit per file
    private static final int MAX_ENTRIES = 1000;

    private ZipDecompressor() {}

    public static Flux<DocumentHistoryDTO> decompress(DocumentHistoryDTO zipHistory, String tempDirPath) {
        if (!Boolean.TRUE.equals(zipHistory.getIsZip()) || zipHistory.getContent() == null) {
            return Flux.just(zipHistory);
        }

        try {
            var tempFilePath = createTempFileWithFallback(tempDirPath);
            var tempFile = tempFilePath.toFile();
            try {
                Files.write(tempFile.toPath(), zipHistory.getContent());
                var entries = processZipFile(tempFile, zipHistory);
                return Flux.fromIterable(entries);
            } finally {
                Files.deleteIfExists(tempFile.toPath());
            }
        } catch (IOException e) {
            return Flux.error(new ProcessingException(
                "Failed to decompress ZIP '" + zipHistory.getFilename() + "': " + e.getMessage(),
                DECOMPRESSION_ERROR.name(),
                e));
        }
    }

    private static List<DocumentHistoryDTO> processZipFile(File tempFile, DocumentHistoryDTO zipHistory) throws IOException {
        var zipFile = openZipFile(tempFile);
        try {
            return extractEntries(zipFile, zipHistory);
        } finally {
            zipFile.close();
        }
    }

    private static ZipFile openZipFile(File tempFile) throws IOException {
        try {
            return new ZipFile(tempFile, java.nio.charset.StandardCharsets.UTF_8);
        } catch (ZipException e) {
            return new ZipFile(tempFile, java.nio.charset.StandardCharsets.ISO_8859_1);
        }
    }

    private static List<DocumentHistoryDTO> extractEntries(ZipFile zipFile, DocumentHistoryDTO zipHistory) throws IOException {
        var entries = new ArrayList<DocumentHistoryDTO>();
        var totalEntries = 0;

        var enumeration = zipFile.entries();
        while (enumeration.hasMoreElements()) {
            var entry = enumeration.nextElement();
            totalEntries++;
            
            if (totalEntries > MAX_ENTRIES) {
                throw new ProcessingException("Too many entries in ZIP (Zip Bomb protection)", DECOMPRESSION_ERROR.name());
            }

            if (entry.isDirectory() || entry.getName() == null || entry.getName().isBlank()) {
                continue;
            }

            var entryName = entry.getName();
            if (entryName.contains("..")) {
                throw new ProcessingException("Zip Slip vulnerability detected: " + entryName, DECOMPRESSION_ERROR.name());
            }

            var decompressed = readEntryContent(zipFile, entry);
            entries.add(buildHistoryEntry(entryName, decompressed, zipHistory));
        }
        return entries;
    }

    private static byte[] readEntryContent(ZipFile zipFile, ZipEntry entry) throws IOException {
        try (var is = zipFile.getInputStream(entry)) {
            var decompressed = is.readNBytes((int) MAX_FILE_SIZE + 1);
            if (decompressed.length > MAX_FILE_SIZE) {
                throw new ProcessingException("ZIP entry exceeds maximum allowed size (Zip Bomb protection)", DECOMPRESSION_ERROR.name());
            }
            return decompressed;
        }
    }

    private static java.nio.file.Path createTempFileWithFallback(String tempDirPath) throws IOException {
        var candidates = new ArrayList<java.nio.file.Path>();

        // 1. Configured path
        if (tempDirPath != null && !tempDirPath.trim().isEmpty()) {
            try {
                candidates.add(java.nio.file.Paths.get(tempDirPath));
            } catch (Exception ignored) {}
        }

        // 2. Default System tmpdir
        var sysTemp = System.getProperty("java.io.tmpdir");
        if (sysTemp != null && !sysTemp.trim().isEmpty()) {
            candidates.add(java.nio.file.Paths.get(sysTemp));
        }

        // 3. User Home subdirectory
        var userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.trim().isEmpty()) {
            candidates.add(java.nio.file.Paths.get(userHome).resolve(".temp-zip-dir"));
        }

        // 4. Current working directory subdirectory
        var userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.trim().isEmpty()) {
            candidates.add(java.nio.file.Paths.get(userDir).resolve("tmp"));
        }

        IOException lastException = null;
        for (var dir : candidates) {
            try {
                if (!java.nio.file.Files.exists(dir)) {
                    java.nio.file.Files.createDirectories(dir);
                }
                return java.nio.file.Files.createTempFile(dir, "decompress-", ".zip");
            } catch (IOException e) {
                lastException = e;
            }
        }

        // Final fallback using system default TempFile behavior
        try {
            return java.nio.file.Files.createTempFile("decompress-", ".zip");
        } catch (IOException e) {
            if (lastException != null) {
                throw new IOException("Failed to create temporary file in any candidate directory. Last error: " + lastException.getMessage(), e);
            }
            throw e;
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
