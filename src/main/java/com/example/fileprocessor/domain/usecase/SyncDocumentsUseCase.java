package com.example.fileprocessor.domain.usecase;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.ERR_DUPLICATED_DOC;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILED;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.NO_SUCURSAL;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PENDING;

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

    /**
     * Executes the document synchronization process for all products.
     * 
     * Secuencia:
     * 1. Registra el inicio de la sincronización.
     * 2. Obtiene todos los productos desde el repositorio maestro.
     * 3. Para cada producto, llama a la sincronización de sus documentos.
     * 4. Finaliza emitiendo un mensaje de completado.
     *
     * @param useCase the use case identifier
     * @return a Mono emitting a completion message
     */
    public Mono<String> execute(String useCase) {
        LOGGER.info("[SYNC] Iniciando sincronización de documentos.");
        return productMasterRepository.getAllProducts()
                .flatMap(product -> syncDocumentsForProduct(product, useCase))
                .then(Mono.just("Document sync completed"));
    }

    /**
     * Synchronizes documents for a specific product by fetching them from the external API and saving them to the local database.
     *
     * Secuencia:
     * 1. Busca la sucursal del producto en la base de datos local.
     * 2. Si no se encuentra la sucursal, crea un documento de error indicando la falta de sucursal y lo guarda, terminando el flujo.
     * 3. Si se encuentra la sucursal, obtiene los documentos del producto desde la API externa.
     * 4. Para cada documento, verifica si ya existe en la base de datos local.
     * 5. Construye el documento a guardar, asignando el estado de error por duplicado si ya existe, o pendiente si es nuevo.
     * 6. Guarda el documento en la base de datos.
     * 7. En caso de error, registra el error y retorna un flujo vacío.
     *
     * @param product the product maestro entity
     * @param useCase the use case identifier
     * @return a Flux of saved Documents
     */
    private Flux<Document> syncDocumentsForProduct(ProductMaestro product, String useCase) {
        return productLocalRepository.findBranchByProductId(product.getProductId())
                .switchIfEmpty(Mono.defer(() -> {
                    Document errorDoc = Document.builder()
                            .productId(product.getProductId())
                            .documentId(NO_SUCURSAL.name())
                            .name(NO_SUCURSAL.name())
                            .state(FAILED.name())
                            .syncMessage(NO_SUCURSAL.value())
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
                                            .state(exists ? ERR_DUPLICATED_DOC.name() : PENDING.name())
                                            .syncMessage(exists ? ERR_DUPLICATED_DOC.value() : null)
                                            .build();
                                    return documentRepository.save(docToSave);
                                })))
                .onErrorResume(e -> {
                    LOGGER.severe("Error syncing documents for product " + product.getProductId() + ": " + e.getMessage());
                    return Flux.empty();
                });
    }
}
