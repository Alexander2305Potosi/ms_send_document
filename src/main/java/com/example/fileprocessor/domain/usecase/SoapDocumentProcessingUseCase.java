package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentSendRequest;
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
    protected Mono<ProductDocumentToProcess> filterByFolder(
            ProductDocumentToProcess pending, String traceId) {

        // SOAP-specific: no additional folder filtering
        // Shared validation rules already applied in preValidate via validationRules
        return Mono.just(pending);
    }

    @Override
    protected Mono<ProductDocumentToProcess> validateDocument(
            ProductDocumentToProcess pending, String traceId) {

        log.info("Validating SOAP document: {}, productId: {}",
            pending.getDocumentId(), pending.getProductId());

        // SOAP-specific validation: only XML, text, and PDF content types
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
    protected int maxConcurrency() {
        return settings.getMaxConcurrency();
    }
}