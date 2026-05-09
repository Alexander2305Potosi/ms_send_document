package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class SyncDocumentsUseCase {

    private static final Logger log = Logger.getLogger(SyncDocumentsUseCase.class.getName());

    private final ProductRepository productRepository;
    private final DocumentRepository documentRepository;
    private final ProductRestGateway productRestGateway;

    public SyncDocumentsUseCase(
            ProductRepository productRepository,
            DocumentRepository documentRepository,
            ProductRestGateway productRestGateway) {
        this.productRepository = productRepository;
        this.documentRepository = documentRepository;
        this.productRestGateway = productRestGateway;
    }

    public Mono<String> execute(String useCase) {
        log.log(Level.INFO, "Starting document sync with useCase: {0}", new Object[]{useCase});
        return productRepository.findAll()
                .concatMap(productRestGateway::getDocumentsByProduct)
                .flatMap(doc -> saveDocument(doc, useCase))
                .then(Mono.just("Document sync completed"))
            .doOnError(e -> log.log(Level.SEVERE, "Document sync failed: " + e.getMessage()));
    }

    private Mono<Void> saveDocument(com.example.fileprocessor.domain.entity.ProductDocumentHistory doc, String useCase) {
        Document document = Document.builder()
            .documentId(doc.documentId())
            .productId(doc.productId())
            .name(doc.filename())
            .owner(doc.productId())
            .useCase(useCase)
            .state(ProductState.PENDING)
            .isZip(doc.isZip())
            .parentZipName(null)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        return documentRepository.save(document)
            .then(Mono.empty());
    }
}
