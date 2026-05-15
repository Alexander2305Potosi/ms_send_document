package com.example.fileprocessor.domain.util;

import com.example.fileprocessor.domain.entity.product.DocumentHistory;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class for decompressing ZIP archives into individual DocumentHistory instances.
 */
public final class ZipDecompressor {

    private ZipDecompressor() {}

    public static Flux<DocumentHistory> decompress(DocumentHistory zipHistory) {
        if (!zipHistory.isZip() || zipHistory.getContent() == null) {
            return Flux.just(zipHistory);
        }

        return Flux.generate(
            () -> new ZipInputStream(new ByteArrayInputStream(zipHistory.getContent())),
            (zis, sink) -> {
                try {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (!entry.isDirectory() && entry.getName() != null && !entry.getName().isBlank()) {
                            byte[] decompressed = zis.readAllBytes();
                            sink.next(buildHistoryEntry(entry.getName(), decompressed, zipHistory));
                            return zis;
                        }
                    }
                    sink.complete();
                } catch (IOException e) {
                    sink.error(new ProcessingException(
                        ProcessingResultCodes.DECOMPRESSION_ERROR.name(),
                        "Failed to decompress ZIP '" + zipHistory.getFilename() + "': " + e.getMessage(),
                        e));
                }
                return zis;
            },
            zis -> {
                try {
                    zis.close();
                } catch (IOException e) {
                    // Silent close
                }
            }
        );
    }

    private static DocumentHistory buildHistoryEntry(String filename, byte[] content, DocumentHistory original) {
        return DocumentHistory.builder()
            .documentId(original.getDocumentId())
            .businessDocumentId(original.getBusinessDocumentId() + "/" + filename)
            .productId(original.getProductId())
            .filename(filename)
            .contentType(MimeTypeUtil.getMimeType(filename))
            .size((long) content.length)
            .isZip(false)
            .pais(original.getPais())
            .content(content)
            .origin(original.getOrigin())
            .startedAt(original.getStartedAt())
            .retry(original.getRetry())
            .build();
    }
}
