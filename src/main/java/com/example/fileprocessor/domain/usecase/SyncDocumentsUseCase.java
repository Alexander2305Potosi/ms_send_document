package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.maestro.ProductMaestro;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductLocalRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.logging.Logger;

/**
 * Use case to synchronize documents from external API to local database.
 */
public class SyncDocumentsUseCase {

    private static final Logger LOGGER = Logger.getLogger(SyncDocumentsUseCase.class.getName());
    private final DocumentRepository documentRepository;
    private final ProductMasterRepository productMasterRepository;
    private final ProductRestGateway productRestGateway;
    private final ProductLocalRepository productLocalRepository;

    public SyncDocumentsUseCase(DocumentRepository documentRepository,
                                ProductMasterRepository productMasterRepository,
                                ProductRestGateway productRestGateway,
                                ProductLocalRepository productLocalRepository) {
        this.documentRepository = documentRepository;
        this.productMasterRepository = productMasterRepository;
        this.productRestGateway = productRestGateway;
        this.productLocalRepository = productLocalRepository;
    }

    public Mono<String> execute(String useCase) {
        LOGGER.info("[SYNC] Iniciando sincronización de documentos.");
        return productMasterRepository.getAllProducts()
                .flatMap(product -> syncDocumentsForProduct(product, useCase))
                .then(Mono.just("Document sync completed"));
    }

    private Flux<Document> syncDocumentsForProduct(ProductMaestro product, String useCase) {
        return productLocalRepository.findBranchByProductId(product.getProductId())
                .switchIfEmpty(Mono.defer(() -> {
                    Document errorDoc = Document.builder()
                            .productId(product.getProductId())
                            .documentId(ProcessingResultCodes.NO_SUCURSAL.name())
                            .name(ProcessingResultCodes.NO_SUCURSAL.name())
                            .state(ProcessingResultCodes.FAILED.name())
                            .syncMessage(ProcessingResultCodes.NO_SUCURSAL.value())
                            .useCase(useCase)
                            .build();
                    return documentRepository.save(errorDoc).then(Mono.empty());
                }))
                .flatMapMany(sucursal -> productRestGateway.getDocumentsByProduct(product)
                        .flatMap(doc -> documentRepository.existsByProductIdAndDocumentId(doc.getProductId(), doc.getDocumentId())
                                .flatMap(exists -> {
                                    Document docToSave = Document.builder()
                                            .productId(doc.getProductId())
                                            .documentId(doc.getDocumentId())
                                            .name(doc.getName())
                                            .isZip(doc.getIsZip())
                                            .useCase(useCase)
                                            .originFolder(product.getOriginFolder())
                                            .originCountry(product.getOriginCountry())
                                            .sucursal(sucursal)
                                            .state(exists ? ProcessingResultCodes.ERR_DUPLICATED_DOC.name() : ProcessingResultCodes.PENDING.name())
                                            .syncMessage(exists ? ProcessingResultCodes.ERR_DUPLICATED_DOC.value() : null)
                                            .build();
                                    return documentRepository.save(docToSave);
                                })))
                .onErrorResume(e -> {
                    LOGGER.severe("Error syncing documents for product " + product.getProductId() + ": " + e.getMessage());
                    return Flux.empty();
                });
    }
}
