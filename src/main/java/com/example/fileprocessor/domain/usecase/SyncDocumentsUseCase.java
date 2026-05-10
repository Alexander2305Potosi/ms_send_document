package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SyncDocumentsUseCase {

    private static final Logger LOGGER = Logger.getLogger(SyncDocumentsUseCase.class.getName());

    private final DocumentRepository documentRepository;
    private final ProductRestGateway productRestGateway;

    public SyncDocumentsUseCase(
            DocumentRepository documentRepository,
            ProductRestGateway productRestGateway) {
        this.documentRepository = documentRepository;
        this.productRestGateway = productRestGateway;
    }

    public Mono<String> execute(String useCase) {
        LOGGER.log(Level.INFO, "Starting document sync with useCase: {0}", new Object[]{useCase});
        return productRestGateway.getAllProducts()
                .concatMap(product -> {
                    LOGGER.log(Level.INFO, "Syncing documents for product ID: {0}", new Object[]{product.getProductId()});
                    return productRestGateway.getDocumentsByProduct(product)
                        .onErrorResume(e -> {
                            LOGGER.log(Level.WARNING, "Failed to sync documents for product {0}: {1}", 
                                    new Object[]{product.getProductId(), e.getMessage()});
                            return reactor.core.publisher.Flux.empty();
                        });
                })
                .concatMap(doc -> saveDocument(doc, useCase))
                .then(Mono.defer(() -> {
                    LOGGER.log(Level.INFO, "Document sync completed successfully for useCase: {0}", new Object[]{useCase});
                    return Mono.just("Document sync completed");
                }))
            .doOnError(e -> LOGGER.log(Level.SEVERE, "Document sync failed fatally: " + e.getMessage()));
    }

    private Mono<Void> saveDocument(com.example.fileprocessor.domain.entity.ProductDocumentHistory doc, String useCase) {
        return documentRepository.existsByProductIdAndDocumentId(doc.getProductId(), doc.getDocumentId())
            .flatMap(exists -> {
                if (Boolean.TRUE.equals(exists)) {
                    LOGGER.log(Level.INFO, "Duplicate document detected for productId={0}, documentId={1}. Marking as ERR_DUPLICATED_DOC.",
                            new Object[]{doc.getProductId(), doc.getDocumentId()});
                    Document duplicate = Document.builder()
                        .documentId(doc.getDocumentId())
                        .productId(doc.getProductId())
                        .name(doc.getFilename())
                        .useCase(useCase)
                        .state(ProductState.ERR_DUPLICATED_DOC)
                        .errorMessage("Documento duplicado detectado para productId=" + doc.getProductId() + " y documentId=" + doc.getDocumentId())
                        .isZip(doc.isZip())
                        .createdAt(LocalDateTime.now())
                        .build();
                    return documentRepository.save(duplicate).then(Mono.empty());
                }

                Document document = Document.builder()
                    .documentId(doc.getDocumentId())
                    .productId(doc.getProductId())
                    .name(doc.getFilename())
                    .useCase(useCase)
                    .state(ProductState.PENDING)
                    .isZip(doc.isZip())
                    .createdAt(LocalDateTime.now())
                    .build();
                return documentRepository.save(document).then(Mono.empty());
            });
    }
}
