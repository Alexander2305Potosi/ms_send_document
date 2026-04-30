package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.ProductDocumentInfo;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.entity.ProductInfo;
import com.example.fileprocessor.domain.entity.ProductToProcess;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Use case for loading products and documents from external REST API.
 */
public class LoadProductsUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoadProductsUseCase.class);

    private final ProductRestGateway productGateway;
    private final ProductRepository productRepository;
    private final ProductDocumentRepository documentRepository;

    public LoadProductsUseCase(ProductRestGateway productGateway,
                               ProductRepository productRepository,
                               ProductDocumentRepository documentRepository) {
        this.productGateway = productGateway;
        this.productRepository = productRepository;
        this.documentRepository = documentRepository;
    }

    public Flux<LoadProductsResult> execute() {
        log.info("Starting products load from REST API");

        return productGateway.getAllProducts()
            .flatMap(this::loadProductAndDocuments)
            .doOnError(error -> log.error("Error loading products: {}", error.getMessage()));
    }

    private Mono<LoadProductsResult> loadProductAndDocuments(ProductInfo productInfo) {
        int docCount = productInfo.getDocuments() != null ? productInfo.getDocuments().size() : 0;
        log.info("Loading product: {} with {} documents",
            productInfo.getProductId(), docCount);

        ProductToProcess product = ProductToProcess.builder()
            .productId(productInfo.getProductId())
            .name(productInfo.getName())
            .status(DocumentStatus.PENDING.name())
            .createdAt(Instant.now())
            .build();

        Flux<ProductDocumentToProcess> documentsFlux = createDocumentsFlux(productInfo);

        return productRepository.save(product)
            .then(documentRepository.saveAll(documentsFlux))
            .thenReturn(LoadProductsResult.builder()
                .productId(productInfo.getProductId())
                .name(productInfo.getName())
                .documentCount(docCount)
                .status(DocumentStatus.PENDING.name())
                .message("Product and documents loaded successfully")
                .processedAt(Instant.now())
                .success(true)
                .build())
            .doOnSuccess(result -> log.info("Product {} loaded with {} documents",
                result.getProductId(), result.getDocumentCount()));
    }

    private Flux<ProductDocumentToProcess> createDocumentsFlux(ProductInfo productInfo) {
        return Flux.fromIterable(productInfo.getDocuments())
            .map(docInfo -> createProductDocument(productInfo.getProductId(), docInfo, null));
    }

    private ProductDocumentToProcess createProductDocument(String productId, ProductDocumentInfo docInfo, String parentId) {
        return ProductDocumentToProcess.builder()
            .documentId(docInfo.documentId())
            .productId(productId)
            .parentDocumentId(parentId)
            .filename(docInfo.filename())
            .content(null)
            .contentType(docInfo.contentType())
            .origin(docInfo.origin())
            .status(DocumentStatus.PENDING.name())
            .createdAt(Instant.now())
            .build();
    }
}
