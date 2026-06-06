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
            File tempFile;
            if (tempDirPath != null && !tempDirPath.trim().isEmpty()) {
                File dir = new File(tempDirPath);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                tempFile = File.createTempFile("decompress-", ".zip", dir);
            } else {
                tempFile = File.createTempFile("decompress-", ".zip");
            }
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
                    Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
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
