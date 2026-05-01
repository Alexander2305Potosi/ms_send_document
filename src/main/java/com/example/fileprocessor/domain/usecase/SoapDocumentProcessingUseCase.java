package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocument;
import com.example.fileprocessor.domain.port.out.ProductDbGateway;
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
            ProductDbGateway productDbGateway,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            RulesBussinesGateway documentValidator) {
        super(productDbGateway, productRestGateway, documentValidator);
        this.soapGateway = soapGateway;
    }

    @Override
    protected Mono<FileUploadResult> uploadDocument(ProductDocument doc, String productId) {
        return soapGateway.send(buildFileUploadRequest(doc, null))
            .onErrorResume(this::handleUploadError);
    }

    @Override
    protected String implementationName() {
        return "SOAP";
    }
}
