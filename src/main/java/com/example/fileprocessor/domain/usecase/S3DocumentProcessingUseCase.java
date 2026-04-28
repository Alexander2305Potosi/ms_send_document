package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.domain.port.out.ResilienceOperator;
import com.example.fileprocessor.domain.valueobject.FolderExclusionRegexConfig;
import com.example.fileprocessor.infrastructure.helpers.config.ProcessorSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * S3-specific document processing use case.
 * Implements validation and request building for S3 gateway.
 */
public class S3DocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private static final Logger log = LoggerFactory.getLogger(S3DocumentProcessingUseCase.class);
    private static final long BYTES_PER_MEGABYTE = 1024L * 1024L;

    private final ProcessorSettings settings;

    public S3DocumentProcessingUseCase(
            ProcessingDependencies deps,
            ResilienceOperator resilienceOperator,
            FileValidator fileValidator,
            DocumentValidationRules validationRules,
            FolderExclusionRegexConfig folderExclusionRegex,
            ProcessorSettings settings) {
        super(deps, resilienceOperator, fileValidator, validationRules, folderExclusionRegex,
            new CommunicationLogFactory("S3"));
        this.settings = settings;
    }

    @Override
    protected String implementationName() {
        return "S3";
    }

    @Override
    protected Mono<ProductDocumentToProcess> validateDocument(
            ProductDocumentToProcess pending, String traceId) {

        log.info("Validating S3 document: {}, productId: {}",
            pending.getDocumentId(), pending.getProductId());

        // S3-specific: size validation (preValidate already handles size via validationRules,
        // but S3 may have different limits, so we check S3-specific max here)
        // Note: size check via shouldNotSendBySize is already in preValidate via validationRules
        // This method focuses on gateway-specific validation before delegation

        // Delegate to common file validator (extension, filename, etc.)
        return fileValidator.validate(pending);
    }

    @Override
    protected Mono<DocumentSendRequest> buildRequest(ProductDocumentToProcess validDoc, String traceId) {
        DocumentValidationRules.FolderInfo folderInfo = validationRules.extractFolderInfo(validDoc.getOrigin());
        String idempotencyKey = IdempotencyKey.forFirstAttempt(validDoc.getDocumentId(), traceId).value();

        DocumentSendRequest request = DocumentSendRequest.builder()
            .documentId(validDoc.getDocumentId())
            .fileContent(validDoc.getContent())
            .filename(validDoc.getFilename())
            .contentType(validDoc.getContentType())
            .fileSize(validDoc.getContent() != null ? validDoc.getContent().length : 0)
            .traceId(traceId)
            .parentFolder(folderInfo.parentFolder())
            .childFolder(folderInfo.childFolder())
            .idempotencyKey(idempotencyKey)
            .build();

        return Mono.just(request);
    }

    @Override
    protected int maxConcurrency() {
        return settings.getMaxConcurrency();
    }
}
