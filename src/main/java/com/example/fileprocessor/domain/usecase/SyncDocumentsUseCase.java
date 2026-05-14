package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Use case for synchronizing documents.
 * Orchestrates Master Database (via Repository) and External API (via Gateway).
 */
public class SyncDocumentsUseCase {

    private static final Logger LOGGER = Logger.getLogger(SyncDocumentsUseCase.class.getName());

    private final DocumentRepository documentRepository;
    private final ProductMasterRepository productMasterRepository;
    private final ProductRestGateway productRestGateway;

    public SyncDocumentsUseCase(
            DocumentRepository documentRepository,
            ProductMasterRepository productMasterRepository,
            ProductRestGateway productRestGateway) {
        this.documentRepository = documentRepository;
        this.productMasterRepository = productMasterRepository;
        this.productRestGateway = productRestGateway;
    }

    public Mono<String> execute(String useCase) {
        LOGGER.log(Level.INFO, "Starting orchestrated sync for useCase: {0}", new Object[]{useCase});
        
        // 1. Obtener listado de productos desde el REPOSITORIO MAESTRO (Base de Datos)
        return productMasterRepository.getAllProducts()
                .concatMap(product -> {
                    LOGGER.log(Level.INFO, "Syncing documents for product {0} via EXTERNAL GATEWAY (API)", new Object[]{product.getProductId()});
                    
                    // 2. Para cada producto, consultar el GATEWAY EXTERNO (API) para traer sus documentos
                    return productRestGateway.getDocumentsByProduct(product)
                        .onErrorResume(e -> {
                            LOGGER.log(Level.WARNING, "Failed to fetch documents from Gateway for product {0}: {1}", 
                                    new Object[]{product.getProductId(), e.getMessage()});
                            return reactor.core.publisher.Flux.empty();
                        });
                })
                .concatMap(doc -> saveDocument(doc, useCase))
                .then(Mono.defer(() -> {
                    LOGGER.log(Level.INFO, "Orchestrated sync completed for useCase: {0}", new Object[]{useCase});
                    return Mono.just("Document sync completed");
                }))
            .doOnError(e -> LOGGER.log(Level.SEVERE, "Orchestrated sync failed: " + e.getMessage()));
    }

    private Mono<Void> saveDocument(ProductDocumentHistory doc, String useCase) {
        return documentRepository.existsByProductIdAndDocumentId(doc.getProductId(), doc.getDocumentId())
                .flatMap(exists -> {

                    Document document = Document.builder()
                            .documentId(doc.getDocumentId())
                            .productId(doc.getProductId())
                            .name(doc.getFilename())
                            .useCase(useCase)
                            .state(ProductState.ERR_DUPLICATED_DOC)
                            .errorMessage("Documento duplicado detectado para productId=" + doc.getProductId() + " y documentId=" + doc.getDocumentId())
                            .isZip(doc.isZip())
                            .createdAt(LocalDateTime.now())
                            .build();

                    if (Boolean.TRUE.equals(exists)) {
                        LOGGER.log(Level.INFO, "Duplicate document detected for productId={0}, documentId={1}. Marking as ERR_DUPLICATED_DOC.",
                                new Object[]{doc.getProductId(), doc.getDocumentId()});

                        document.setState(ProductState.ERR_DUPLICATED_DOC);
                        document.setErrorMessage("Documento duplicado detectado para productId=" + doc.getProductId() + " y documentId=" + doc.getDocumentId());
                    } else {
                        document.setState(ProductState.PENDING);
                    }

                    return documentRepository.save(document).then(Mono.empty());
                });
    }
}
