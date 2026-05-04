package com.example.fileprocessor.domain.util;

import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class for decompressing ZIP archives into individual ProductDocument instances.
 */
public final class ZipDecompressor {

    private ZipDecompressor() {}

    public static Flux<ProductDocumentHistory> decompress(ProductDocumentHistory zipDoc) {
        if (!zipDoc.isZip()) {
            return Flux.just(zipDoc);
        }

        List<ProductDocumentHistory> entries = readZipEntries(zipDoc);
        if (entries.isEmpty()) {
            return Flux.empty();
        }
        return Flux.fromIterable(entries);
    }

    private static List<ProductDocumentHistory> readZipEntries(ProductDocumentHistory zipDoc) {
        List<ProductDocumentHistory> entries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(
                new ByteArrayInputStream(zipDoc.content()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName() != null && !entry.getName().isBlank()) {
                    byte[] decompressed = zis.readAllBytes();
                    ProductDocumentHistory expanded = buildProductDocument(entry.getName(), decompressed, zipDoc);
                    entries.add(expanded);
                }
            }
        } catch (IOException e) {
            throw ProcessingException.withTraceId(
                "Failed to decompress ZIP: " + zipDoc.documentId(),
                ProcessingResultCodes.INVALID_ZIP, zipDoc.documentId());
        }
        return entries;
    }

    private static ProductDocumentHistory buildProductDocument(String filename, byte[] content, ProductDocumentHistory original) {
        return ProductDocumentHistory.builder()
            .documentId(original.documentId() + "/" + filename)
            .filename(filename)
            .content(content)
            .contentType(inferContentType(filename))
            .size(content.length)
            .isZip(false)
            .origin(original.origin())
            .pais(original.pais())
            .build();
    }

    private static String inferContentType(String filename) {
        if (filename.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (filename.endsWith(".csv")) {
            return "text/csv";
        }
        if (filename.endsWith(".txt")) {
            return "text/plain";
        }
        if (filename.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (filename.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        return "application/octet-stream";
    }
}
