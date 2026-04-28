package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.ResilienceOperator;
import com.example.fileprocessor.domain.valueobject.FolderExclusionRegexConfig;
import com.example.fileprocessor.infrastructure.helpers.config.ProcessorSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * S3-specific document processing use case.
 */
public class S3DocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private static final Logger log = LoggerFactory.getLogger(S3DocumentProcessingUseCase.class);
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
    protected Mono<ProductDocumentToProcess> filterByFolder(
            ProductDocumentToProcess pending, String traceId) {

        // S3-specific: check folder exclusion regex
        if (folderExclusionRegex.shouldExclude(pending.getOrigin())) {
            log.info("S3 document {} skipped: origin matches exclusion regex: {}",
                pending.getFilename(), pending.getOrigin());
            return skipDocumentByOrigin(pending, traceId,
                "Folder excluded by regex: " + pending.getOrigin(),
                ProcessingResultCodes.SKIPPED_FOLDER);
        }

        return Mono.just(pending);
    }

    @Override
    protected Mono<ProductDocumentToProcess> validateDocument(
            ProductDocumentToProcess pending, String traceId) {

        log.info("Validating S3 document: {}, productId: {}",
            pending.getDocumentId(), pending.getProductId());

        // Delegate to common file validator
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

    private Mono<ProductDocumentToProcess> skipDocumentByOrigin(
            ProductDocumentToProcess pending, String traceId, String message, String errorCode) {
        log.info("Document {} skipped: {}", pending.getDocumentId(), message);
        return documentRepository.updateStatus(
            pending.getDocumentId(), DocumentStatus.SKIPPED.name(), traceId, null, errorCode)
            .thenReturn(pending);
    }
}