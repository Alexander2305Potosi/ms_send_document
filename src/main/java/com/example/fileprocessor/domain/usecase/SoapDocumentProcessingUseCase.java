package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
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
            ProductDocumentRepository documentRepository,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway) {
        super(documentRepository, productRestGateway);
        this.soapGateway = soapGateway;
    }

    @Override
    protected String implementationName() {
        return "SOAP";
    }

    @Override
    protected Mono<ProductDocumentToProcess> applyRules(ProductDocumentToProcess doc) {
        return Mono.just(doc);
    }

    @Override
    protected Mono<FileUploadResult> uploadDocument(ProductDocumentToProcess doc) {
        if (doc.isSkipped()) {
            return Mono.just(FileUploadResult.builder()
                .status(DocumentStatus.SKIPPED.name())
                .correlationId(doc.getDocumentId())
                .processedAt(Instant.now())
                .success(true)
                .message("Document skipped")
                .build());
        }

        return soapGateway.send(
                doc.getDocumentId(),
                doc.getContent(),
                doc.getFilename(),
                doc.getContentType(),
                doc.getFileSizeBytes(),
                doc.getParentFolder(),
                doc.getChildFolder())
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
