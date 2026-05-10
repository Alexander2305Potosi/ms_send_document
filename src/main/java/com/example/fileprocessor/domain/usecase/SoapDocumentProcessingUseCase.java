package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.logging.Level;

/**
 * Use case for processing documents via SOAP.
 */
public class SoapDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private final SoapGateway soapGateway;
    private final RulesBussinesGateway documentValidator;

    public SoapDocumentProcessingUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            RulesBussinesGateway documentValidator) {
        super(persistencePort, productRestGateway, documentValidator);
        this.soapGateway = soapGateway;
        this.documentValidator = documentValidator;
    }

    @Override
    protected Mono<FileUploadResponse> uploadDocument(ProductDocumentHistory doc, String productId, Long docId) {
        return Mono.just(FileUploadRequest.from(doc, docId))
            .flatMap(soapGateway::send)
            .onErrorResume(e -> {
                LOGGER.log(Level.SEVERE, "SOAP processing failed for docId {0}: {1}", new Object[]{docId, e.getMessage()});
                return Mono.just(FileUploadResponse.builder()
                    .status(ProcessingResultCodes.FAILURE.name())
                    .success(false)
                    .message(e.getMessage())
                    .processedAt(Instant.now())
                    .build());
            });
    }

    @Override
    protected String implementationName() {
        return "SOAP";
    }
}
