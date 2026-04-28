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
 * SOAP-specific document processing use case.
 * Implements validation and request building for SOAP gateway.
 */
public class SoapDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private static final Logger log = LoggerFactory.getLogger(SoapDocumentProcessingUseCase.class);

    private final ProcessorSettings settings;

    public SoapDocumentProcessingUseCase(
            ProcessingDependencies deps,
            ResilienceOperator resilienceOperator,
            FileValidator fileValidator,
            DocumentValidationRules validationRules,
            FolderExclusionRegexConfig folderExclusionRegex,
            ProcessorSettings settings) {
        super(deps, resilienceOperator, fileValidator, validationRules, folderExclusionRegex,
            new CommunicationLogFactory("SOAP"));
        this.settings = settings;
    }

    @Override
    protected String implementationName() {
        return "SOAP";
    }

    @Override
    protected Mono<ProductDocumentToProcess> validateDocument(
            ProductDocumentToProcess pending, String traceId) {

        log.info("Validating SOAP document: {}, productId: {}",
            pending.getDocumentId(), pending.getProductId());

        // SOAP-specific validation: only XML and text content types
        String contentType = pending.getContentType();
        if (contentType == null ||
            (!contentType.contains("xml") && !contentType.contains("text") && !contentType.contains("pdf"))) {
            log.warn("SOAP document {} rejected: unsupported content type {}",
                pending.getFilename(), contentType);
            return Mono.error(new FileValidationException(
                "Unsupported SOAP content type: " + contentType,
                ProcessingResultCodes.INVALID_FILE_TYPE));
        }

        // Delegate to common file validator
        return fileValidator.validate(pending);
    }

    @Override
    protected Mono<DocumentSendRequest> buildRequest(ProductDocumentToProcess validDoc, String traceId) {
        DocumentValidationRules.FolderInfo folderInfo = validationRules.extractFolderInfo(validDoc.getOrigin());

        DocumentSendRequest request = DocumentSendRequest.builder()
            .documentId(validDoc.getDocumentId())
            .fileContent(validDoc.getContent())
            .filename(validDoc.getFilename())
            .contentType(validDoc.getContentType())
            .fileSize(validDoc.getContent() != null ? validDoc.getContent().length : 0)
            .traceId(traceId)
            .parentFolder(folderInfo.parentFolder())
            .childFolder(folderInfo.childFolder())
            .build();

        return Mono.just(request);
    }

    @Override
    protected int maxConcurrency() {
        return settings.getMaxConcurrency();
    }
}
