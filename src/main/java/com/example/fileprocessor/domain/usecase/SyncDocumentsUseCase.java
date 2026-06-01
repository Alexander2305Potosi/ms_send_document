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
        return documentRepository.findLastProcessedProductIdInRange()
                .defaultIfEmpty("")
                .flatMapMany(lastProductId -> {
                    if (lastProductId.isEmpty()) {
                        LOGGER.info("[SYNC] Iniciando sincronización completa (sin registros previos en el rango).");
                    } else {
                        LOGGER.info("[SYNC] Reanudando sincronización a partir del id_producto: " + lastProductId);
                    }
                    // Inyectar el cursor en el contexto reactivo solo cuando existe un ID previo
                    return productMasterRepository.getAllProducts()
                            .contextWrite(ctx -> lastProductId.isEmpty()
                                    ? ctx
                                    : ctx.put("last_product_id", lastProductId));
                })
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
                                    doc.setUseCase(useCase);
                                    doc.setOriginFolder(product.getOriginFolder());
                                    doc.setOriginCountry(product.getOriginCountry());
                                    doc.setSucursal(sucursal);
                                    if (exists) {
                                        doc.setState(ProcessingResultCodes.ERR_DUPLICATED_DOC.name());
                                        doc.setSyncMessage(ProcessingResultCodes.ERR_DUPLICATED_DOC.value());
                                    } else {
                                        doc.setState(ProcessingResultCodes.PENDING.name());
                                    }
                                    return documentRepository.save(doc);
                                })))
                .onErrorResume(e -> {
                    LOGGER.severe("Error syncing documents for product " + product.getProductId() + ": " + e.getMessage());
                    return Flux.empty();
                });
    }
}
