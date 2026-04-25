package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
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
import java.util.UUID;

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
        String traceId = UUID.randomUUID().toString();
        log.info("Starting products load from REST API, traceId: {}", traceId);

        return productGateway.getAllProducts(traceId)
            .flatMap(this::loadProductAndDocuments)
            .doOnError(error -> log.error("Error loading products: {}", error.getMessage()));
    }

    private Flux<LoadProductsResult> loadProductAndDocuments(ProductInfo productInfo) {
        String traceId = UUID.randomUUID().toString();
        log.info("Loading product: {} with {} documents",
            productInfo.getProductId(), productInfo.getDocuments().size());

        ProductToProcess product = ProductToProcess.builder()
            .productId(productInfo.getProductId())
            .name(productInfo.getName())
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .traceId(traceId)
            .build();

        Flux<ProductDocumentToProcess> documents = Flux.fromIterable(productInfo.getDocuments())
            .map(docInfo -> ProductDocumentToProcess.builder()
                .documentId(docInfo.documentId())
                .productId(productInfo.getProductId())
                .filename(docInfo.filename())
                .origin(docInfo.origin())
                .status(DocumentStatus.PENDING_VALUE)
                .createdAt(Instant.now())
                .build());

        return productRepository.save(product)
            .then(documentRepository.saveAll(documents))
            .then(Mono.fromCallable(() -> LoadProductsResult.builder()
                .productId(productInfo.getProductId())
                .name(productInfo.getName())
                .documentCount(productInfo.getDocuments().size())
                .status(DocumentStatus.PENDING_VALUE)
                .message("Product and documents loaded successfully")
                .traceId(traceId)
                .processedAt(Instant.now())
                .success(true)
                .build()))
            .flatMapMany(result -> Flux.just(result))
            .doOnNext(result -> log.info("Product {} loaded with {} documents",
                result.getProductId(), result.getDocumentCount()));
    }
}
