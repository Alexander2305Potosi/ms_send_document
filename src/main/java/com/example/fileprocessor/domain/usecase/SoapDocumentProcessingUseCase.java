package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocument;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.service.DocumentValidator;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * SOAP-specific document processing use case.
 */
public class SoapDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private final SoapGateway soapGateway;

    public SoapDocumentProcessingUseCase(
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            DocumentValidator documentValidator) {
        super(productRestGateway, documentValidator);
        this.soapGateway = soapGateway;
    }

    @Override
    protected Mono<FileUploadResult> uploadDocument(ProductDocument doc, String productId) {
        return soapGateway.send(buildFileUploadRequest(doc, null))
            .onErrorResume(error -> {
                String errorCode = error instanceof com.example.fileprocessor.domain.exception.ProcessingException pe
                    ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
                return Mono.just(FileUploadResult.builder()
                    .status(DocumentStatus.FAILURE.name())
                    .errorCode(errorCode)
                    .processedAt(Instant.now())
                    .success(false)
                    .build());
            });
    }

    @Override
    protected String implementationName() {
        return "SOAP";
    }
}
