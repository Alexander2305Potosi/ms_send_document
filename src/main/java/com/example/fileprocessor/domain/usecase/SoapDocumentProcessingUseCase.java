package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.logging.Level;

/**
 * Use case for processing documents via SOAP.
 */
public class SoapDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private final SoapGateway soapGateway;
    private final HomologationRepository homologationRepository;

    public SoapDocumentProcessingUseCase(
            DocumentRepository documentRepository,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            HomologationRepository homologationRepository,
            RulesBussinesGateway documentValidator,
            DocumentHistoryRepository historyRepository,
            TransactionalOperator transactionalOperator) {
        super(documentRepository, productRestGateway, documentValidator, historyRepository, transactionalOperator);
        this.soapGateway = soapGateway;
        this.homologationRepository = homologationRepository;
    }

    @Override
    protected Mono<FileUploadResponse> uploadDocument(ProductDocumentHistory doc, String productId, Long docId) {
        return homologationRepository.resolve(doc.origin(), doc.pais())
            .map(h -> FileUploadRequest.from(doc, docId))
            .flatMap(soapGateway::send)
            .onErrorResume(e -> {
                log.log(Level.SEVERE, "SOAP processing failed for docId {0}: {1}", new Object[]{docId, e.getMessage()});
                return Mono.just(FileUploadResponse.builder()
                    .status(DocumentStatus.FAILURE.name())
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
