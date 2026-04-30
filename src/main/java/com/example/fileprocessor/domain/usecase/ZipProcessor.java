package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.entity.ZipArchive;
import com.example.fileprocessor.domain.entity.ZipArchive.ExtractedDocument;
import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Unified ZIP archive processing.
 * Handles extraction, validation, and child document creation.
 * Returns result objects that the caller handles via its own repository.
 */
public class ZipProcessor {

    private static final Logger log = LoggerFactory.getLogger(ZipProcessor.class);

    private final FileValidator fileValidator;
    private final int maxEntries;
    private final long maxUncompressedSize;

    public ZipProcessor(FileValidator fileValidator) {
        this(fileValidator, DEFAULT_MAX_ENTRIES, DEFAULT_MAX_UNCOMPRESSED_SIZE);
    }

    public ZipProcessor(FileValidator fileValidator,
                        int maxEntries, long maxUncompressedSize) {
        this.fileValidator = fileValidator;
        this.maxEntries = maxEntries;
        this.maxUncompressedSize = maxUncompressedSize;
    }

    /**
     * Result of ZIP extraction and validation.
     */
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

    /**
     * Result of ZIP children processing.
     */
    public record ZipProcessingResult(
            ProductDocumentToProcess zipDoc,
            List<ProductDocumentToProcess> children,
            List<FileUploadResult> childResults,
            boolean allSucceeded,
            String errorCode
    ) {}

    /**
     * Extracts and validates ZIP children without persisting.
     * Returns result with children ready for caller to save.
     */
    public Mono<ZipExtractionResult> extractAndValidate(ProductDocumentToProcess zipDoc) {
        return extractChildren(zipDoc)
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

    /**
     * Processes ZIP children with given callbacks and returns aggregated result.
     * Does NOT persist children - caller must save them before calling this.
     */
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

    // ============ PRIVATE ============

    private Mono<List<ExtractedDocument>> extractChildren(ProductDocumentToProcess zipDoc) {
        try {
            ZipArchive archive = ZipArchive.builder()
                .zipContent(zipDoc.getContent())
                .originalFilename(zipDoc.getFilename())
                .maxEntries(maxEntries)
                .maxUncompressedSize(maxUncompressedSize)
                .build();

            List<ExtractedDocument> children = archive.extractDocuments();
            log.info("ZIP {} contains {} documents", zipDoc.getFilename(), children.size());
            return Mono.just(children);
        } catch (FileValidationException e) {
            log.error("ZIP validation failed for {}: {}", zipDoc.getFilename(), e.getMessage());
            return Mono.error(e);
        } catch (Exception e) {
            log.error("Failed to extract ZIP {}: {}", zipDoc.getFilename(), e.getMessage());
            return Mono.error(new FileValidationException(
                "ZIP extraction failed: " + e.getMessage(),
                ProcessingResultCodes.ZIP_EXTRACTION_FAILED));
        }
    }

    private Mono<List<ExtractedDocument>> validateChildren(List<ExtractedDocument> children) {
        FileValidationConfig config = fileValidator.getConfig();
        return Flux.fromIterable(children)
            .flatMapSequential(entry -> validateEntry(entry, config))
            .collectList();
    }

    private Mono<ExtractedDocument> validateEntry(ExtractedDocument entry, FileValidationConfig config) {
        if (entry.getFilename().isEmpty() || entry.getFilename().startsWith("_")) {
            return Mono.empty();
        }

        long maxSize = config.maxSize();
        if (entry.getSize() > maxSize) {
            log.warn("ZIP entry {} exceeds max size: {} > {} bytes",
                entry.getFilename(), entry.getSize(), maxSize);
            return Mono.error(new FileValidationException(
                "ZIP entry '" + entry.getFilename() + "' size " + entry.getSize() + " exceeds limit " + maxSize,
                ProcessingResultCodes.FILE_SIZE_EXCEEDED));
        }

        String ext = extension(entry.getFilename());
        Set<String> allowedExtensions = parseAllowedExtensions(config.allowedTypes());
        if (!allowedExtensions.contains(ext.toLowerCase())) {
            log.warn("ZIP entry {} has disallowed extension: '{}'",
                entry.getFilename(), ext);
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
            .build();
    }

    private String extension(String filename) {
        int lastDot = filename != null ? filename.lastIndexOf('.') : -1;
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }

    private static final int DEFAULT_MAX_ENTRIES = 1000;
    private static final long DEFAULT_MAX_UNCOMPRESSED_SIZE = 100 * 1024 * 1024; // 100MB

    private Set<String> parseAllowedExtensions(String allowedTypes) {
        if (allowedTypes == null || allowedTypes.isBlank()) {
            return Set.of();
        }
        return Set.of(allowedTypes.split(",")).stream()
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(ext -> !ext.isBlank())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}