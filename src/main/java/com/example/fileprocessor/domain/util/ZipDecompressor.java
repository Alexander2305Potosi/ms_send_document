package com.example.fileprocessor.domain.util;

import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import org.springframework.http.MediaTypeFactory;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class for decompressing ZIP archives into individual ProductDocument instances.
 * Refactored to process entries as a stream (Flux) to avoid memory exhaustion (OOM).
 */
public final class ZipDecompressor {

    private ZipDecompressor() {}

    public static Flux<ProductDocumentHistory> decompress(ProductDocumentHistory zipDoc) {
        if (!zipDoc.isZip() || zipDoc.content() == null) {
            return Flux.just(zipDoc);
        }

        return Flux.generate(
            () -> new ZipInputStream(new ByteArrayInputStream(zipDoc.content())),
            (zis, sink) -> {
                try {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (!entry.isDirectory() && entry.getName() != null && !entry.getName().isBlank()) {
                            byte[] decompressed = zis.readAllBytes();
                            sink.next(buildProductDocument(entry.getName(), decompressed, zipDoc));
                            return zis; // Emit one entry and wait for next request
                        }
                    }
                    sink.complete();
                } catch (IOException e) {
                    sink.error(new ProcessingException(
                        ProcessingResultCodes.DECOMPRESSION_ERROR.name(),
                        "Failed to decompress ZIP '" + zipDoc.filename() + "': " + e.getMessage(),
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

    private static ProductDocumentHistory buildProductDocument(String filename, byte[] content, ProductDocumentHistory original) {
        return ProductDocumentHistory.builder()
            .productId(original.productId())
            .documentId(original.documentId() + "/" + filename)
            .filename(filename)
            .contentType(inferContentType(filename))
            .size((long) content.length)
            .isZip(false)
            .pais(original.pais())
            .parentZipName(original.filename())
            .content(content)
            .build();
    }

    /**
     * Infers the content type based on the filename using Spring's MediaTypeFactory.
     * This avoids hardcoded if-else blocks and supports standard MIME types automatically.
     */
    private static String inferContentType(String filename) {
        return MediaTypeFactory.getMediaType(filename)
                .map(Object::toString)
                .orElse("application/octet-stream");
    }
}
