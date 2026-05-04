package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import reactor.core.publisher.Mono;

/**
 * SOAP-specific document processing use case.
 */
public class SoapDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private final SoapGateway soapGateway;

    public SoapDocumentProcessingUseCase(
            ProductRepository productRepository,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            DocumentHistoryRepository historyRepository,
            RulesBussinesGateway documentValidator) {
        super(productRepository, historyRepository, productRestGateway, documentValidator);
        this.soapGateway = soapGateway;
    }

    @Override
    protected Mono<FileUploadResult> uploadDocument(ProductDocumentHistory doc, String productId) {
        return soapGateway.send(buildFileUploadRequest(doc, null))
            .onErrorResume(this::handleUploadError);
    }

    @Override
    protected String implementationName() {
        return "SOAP";
    }
}