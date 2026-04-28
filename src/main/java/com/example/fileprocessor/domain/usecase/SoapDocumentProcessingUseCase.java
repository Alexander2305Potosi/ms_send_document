package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.infrastructure.helpers.config.ProcessorSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * SOAP-specific document processing use case.
 */
public class SoapDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private static final Logger log = LoggerFactory.getLogger(SoapDocumentProcessingUseCase.class);

    public SoapDocumentProcessingUseCase(
            ProcessingDependencies deps,
            FileValidator fileValidator,
            ProcessorSettings settings) {
        super(deps, fileValidator, new CommunicationLogFactory("SOAP"));
    }

    @Override
    protected String implementationName() {
        return "SOAP";
    }

    @Override
    protected Mono<ProductDocumentToProcess> filterByFolder(
            ProductDocumentToProcess pending, String traceId) {
        return Mono.just(pending);
    }

    @Override
    protected Mono<ProductDocumentToProcess> validateDocument(
            ProductDocumentToProcess pending, String traceId) {

        log.info("Validating SOAP document: {}, productId: {}",
            pending.getDocumentId(), pending.getProductId());

        String contentType = pending.getContentType();
        if (contentType == null ||
            (!contentType.contains("xml") && !contentType.contains("text") && !contentType.contains("pdf"))) {
            log.warn("SOAP document {} rejected: unsupported content type {}",
                pending.getFilename(), contentType);
            return Mono.error(new FileValidationException(
                "Unsupported SOAP content type: " + contentType,
                ProcessingResultCodes.INVALID_FILE_TYPE));
        }

        return fileValidator.validate(pending);
    }
}