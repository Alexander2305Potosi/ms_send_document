package com.example.fileprocessor.domain.util;

import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class for decompressing ZIP archives into individual ProductDocument instances.
 * Refactored to process entries as a stream (Flux) to avoid memory exhaustion (OOM).
 * This class is now pure domain logic and depends on MimeTypeResolver port.
 */
public final class ZipDecompressor {

    private ZipDecompressor() {}

    /**
     * Decompresses a ZIP file into individual documents.
     * @param zipDoc The ZIP document to decompress.
     * @return A Flux of decompressed documents.
     */
    public static Flux<ProductDocumentHistory> decompress(ProductDocumentHistory zipDoc) {
        if (!zipDoc.isZip() || zipDoc.getContent() == null) {
            return Flux.just(zipDoc);
        }

        return Flux.generate(
            () -> new ZipInputStream(new ByteArrayInputStream(zipDoc.getContent())),
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
                        "Failed to decompress ZIP '" + zipDoc.getFilename() + "': " + e.getMessage(),
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

    private static ProductDocumentHistory buildProductDocument(String filename, byte[] content, 
                                                             ProductDocumentHistory original) {
        return ProductDocumentHistory.builder()
            .productId(original.getProductId())
            .documentId(original.getDocumentId() + "/" + filename)
            .filename(filename)
            .contentType(MimeTypeUtil.getMimeType(filename))
            .size((long) content.length)
            .isZip(false)
            .pais(original.getPais())
            .content(content)
            .build();
    }
}
