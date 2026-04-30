package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentInfo;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * SOAP-specific document processing use case.
 */
public class SoapDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private static final Logger log = LoggerFactory.getLogger(SoapDocumentProcessingUseCase.class);

    private final SoapGateway soapGateway;

    public SoapDocumentProcessingUseCase(
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway) {
        super(productRestGateway);
        this.soapGateway = soapGateway;
    }

    @Override
    protected String implementationName() {
        return "SOAP";
    }

    @Override
    protected Mono<FileUploadResult> uploadDocument(ProductDocumentInfo doc, String productId) {
        byte[] content = doc.content() != null ? doc.content() : new byte[0];
        long fileSizeBytes = content.length;

        return soapGateway.send(
                doc.documentId(),
                content,
                doc.filename(),
                doc.contentType(),
                fileSizeBytes,
                ".",
                ".")
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
}
