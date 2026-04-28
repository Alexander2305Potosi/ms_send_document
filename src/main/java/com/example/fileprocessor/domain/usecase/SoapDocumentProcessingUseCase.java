package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.FileGateway;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * SOAP-specific document processing use case.
 */
public class SoapDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private static final Logger log = LoggerFactory.getLogger(SoapDocumentProcessingUseCase.class);

    public SoapDocumentProcessingUseCase(
            ProductDocumentRepository documentRepository,
            ProductStatusAggregator statusAggregator,
            FileGateway fileGateway,
            FileValidator fileValidator,
            ProductRestGateway productRestGateway) {
        super(documentRepository, statusAggregator, fileGateway, fileValidator, productRestGateway);
    }

    @Override
    protected String implementationName() {
        return "SOAP";
    }

    @Override
    protected Mono<ProductDocumentToProcess> prepareDocument(
            ProductDocumentToProcess pending, String traceId) {
        log.info("Preparing SOAP document: {}, productId: {}",
            pending.getDocumentId(), pending.getProductId());
        return fileValidator.validate(pending);
    }
}