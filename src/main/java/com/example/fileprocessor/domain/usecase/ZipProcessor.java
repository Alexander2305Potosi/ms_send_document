package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.entity.ZipArchive;
import com.example.fileprocessor.domain.entity.ZipArchive.ExtractedDocument;
import com.example.fileprocessor.domain.exception.FileValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ZIP archive processing: extraction, validation, and child document creation.
 */
public class ZipProcessor {

    private static final Logger log = LoggerFactory.getLogger(ZipProcessor.class);

    private static final int DEFAULT_MAX_ENTRIES = 1000;
    private static final long DEFAULT_MAX_UNCOMPRESSED_SIZE = 100L * 1024 * 1024; // 100MB

    private static final double BYTES_TO_MB = 1024.0 * 1024.0;

    private final double maxSizeMb;
    private final Set<String> allowedExtensions;
    private final int maxEntries;
    private final long maxUncompressedSize;

    public ZipProcessor(double maxSizeMb, String allowedTypes) {
        this(maxSizeMb, allowedTypes, DEFAULT_MAX_ENTRIES, DEFAULT_MAX_UNCOMPRESSED_SIZE);
    }

    public ZipProcessor(double maxSizeMb, String allowedTypes, int maxEntries, long maxUncompressedSize) {
        this.maxSizeMb = maxSizeMb;
        this.allowedExtensions = parseExtensions(allowedTypes);
        this.maxEntries = maxEntries;
        this.maxUncompressedSize = maxUncompressedSize;
    }

    public record ZipExtractionResult(
            List<ProductDocumentToProcess> children,
            boolean hasChildren,
            boolean extractionFailed,
            String errorCode
    ) {
        public static ZipExtractionResult success(List<ProductDocumentToProcess> children, boolean hasChildren) {
            return new ZipExtractionResult(children, hasChildren, false, null);
        }

        public static ZipExtractionResult failed(String errorCode) {
            return new ZipExtractionResult(List.of(), false, true, errorCode);
        }
    }

    public record ZipProcessingResult(
            ProductDocumentToProcess zipDoc,
            List<ProductDocumentToProcess> children,
            List<FileUploadResult> childResults,
            boolean allSucceeded,
            String errorCode
    ) {}

    public Mono<ZipExtractionResult> extractAndValidate(ProductDocumentToProcess zipDoc) {
        return extractChildren(zipDoc)
            .publishOn(Schedulers.boundedElastic())
            .flatMap(this::validateChildren)
            .map(entries -> {
                if (entries.isEmpty()) {
                    return ZipExtractionResult.success(List.of(), false);
                }
                List<ProductDocumentToProcess> children = entries.stream()
                    .map(this::toProductDocument)
                    .toList();
                return ZipExtractionResult.success(children, true);
            })
            .onErrorReturn(FileValidationException.class,
                ZipExtractionResult.failed(ProcessingResultCodes.ZIP_EXTRACTION_FAILED))
            .onErrorReturn(Exception.class,
                ZipExtractionResult.failed(ProcessingResultCodes.ZIP_EXTRACTION_FAILED));
    }

    public Mono<ZipProcessingResult> processZipChildren(
            ProductDocumentToProcess zipDoc,
            List<ProductDocumentToProcess> children,
            Function<ProductDocumentToProcess, Mono<DocumentToUpload>> validateMetadataDocument,
            Function<DocumentToUpload, Mono<FileUploadResult>> uploadDocument) {
        return Flux.fromIterable(children)
            .flatMapSequential(child -> validateMetadataDocument.apply(child))
            .flatMap(uploadDocument)
            .collectList()
            .map(results -> {
                boolean allSuccess = results.stream().allMatch(FileUploadResult::isSuccess);
                String errorCode = allSuccess ? null : ProcessingResultCodes.ZIP_PARTIAL_FAILURE;
                return new ZipProcessingResult(zipDoc, children, results, allSuccess, errorCode);
            });
    }

    private Mono<List<ExtractedDocument>> extractChildren(ProductDocumentToProcess zipDoc) {
        return Mono.fromCallable(() -> doExtractChildren(zipDoc))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnError(e -> log.error("Failed to extract ZIP {}: {}", zipDoc.getFilename(), e.getMessage()));
    }

    private List<ExtractedDocument> doExtractChildren(ProductDocumentToProcess zipDoc) {
        try {
            ZipArchive archive = ZipArchive.builder()
                .zipContent(zipDoc.getContent())
                .originalFilename(zipDoc.getFilename())
                .maxEntries(maxEntries)
                .maxUncompressedSize(maxUncompressedSize)
                .build();

            List<ExtractedDocument> children = archive.extractDocuments();
            log.info("ZIP {} contains {} documents", zipDoc.getFilename(), children.size());
            return children;
        } catch (FileValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new FileValidationException(
                "ZIP extraction failed: " + e.getMessage(),
                ProcessingResultCodes.ZIP_EXTRACTION_FAILED);
        }
    }

    private Mono<List<ExtractedDocument>> validateChildren(List<ExtractedDocument> children) {
        return Flux.fromIterable(children)
            .flatMapSequential(this::validateEntry)
            .collectList();
    }

    private Mono<ExtractedDocument> validateEntry(ExtractedDocument entry) {
        if (entry.getFilename().isEmpty() || entry.getFilename().startsWith("_")) {
            return Mono.empty();
        }

        double sizeMb = entry.getSize() / BYTES_TO_MB;
        if (sizeMb > maxSizeMb) {
            log.warn("ZIP entry {} exceeds max size: {} > {} MB",
                entry.getFilename(), sizeMb, maxSizeMb);
            return Mono.error(new FileValidationException(
                "ZIP entry '" + entry.getFilename() + "' size " + String.format("%.2f", sizeMb) + " MB exceeds limit " + maxSizeMb + " MB",
                ProcessingResultCodes.FILE_SIZE_EXCEEDED));
        }

        String ext = extractExtension(entry.getFilename());
        if (!allowedExtensions.contains(ext)) {
            log.warn("ZIP entry {} has disallowed extension: '{}'", entry.getFilename(), ext);
            return Mono.error(new FileValidationException(
                "ZIP entry '" + entry.getFilename() + "' has disallowed type: '" + ext + "'",
                ProcessingResultCodes.INVALID_FILE_TYPE));
        }

        return Mono.just(entry);
    }

    private ProductDocumentToProcess toProductDocument(ExtractedDocument entry) {
        return ProductDocumentToProcess.builder()
            .documentId(entry.getFilename())
            .filename(entry.getFilename())
            .content(entry.getContent())
            .contentType(entry.getContentType())
            .createdAt(Instant.now())
            .isZipArchive(false)
            .fileSizeMb(entry.getSize() / BYTES_TO_MB)
            .build();
    }

    private static String extractExtension(String filename) {
        int lastDot = filename != null ? filename.lastIndexOf('.') : -1;
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }

    private static Set<String> parseExtensions(String allowedTypes) {
        if (allowedTypes == null || allowedTypes.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(allowedTypes.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(ext -> !ext.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }
}
