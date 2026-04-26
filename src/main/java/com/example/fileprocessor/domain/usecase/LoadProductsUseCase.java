package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.ProductDocumentInfo;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.entity.ProductInfo;
import com.example.fileprocessor.domain.entity.ProductToProcess;
import com.example.fileprocessor.domain.entity.ZipArchive;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

public class LoadProductsUseCase {

    private static final Logger log = LoggerFactory.getLogger(LoadProductsUseCase.class);
    private static final String MSG_PRODUCT_LOADED = "Product and documents loaded successfully";

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
        int docCount = productInfo.getDocuments() != null ? productInfo.getDocuments().size() : 0;
        log.info("Loading product: {} with {} documents",
            productInfo.getProductId(), docCount);

        ProductToProcess product = ProductToProcess.builder()
            .productId(productInfo.getProductId())
            .name(productInfo.getName())
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .traceId(traceId)
            .build();

        Flux<ProductDocumentToProcess> documentsFlux = createDocumentsFlux(productInfo, traceId);

        return productRepository.save(product)
            .then(documentRepository.saveAll(documentsFlux))
            .thenMany(Mono.fromCallable(() -> {
                int documentCount = productInfo.getDocuments() != null
                    ? productInfo.getDocuments().size()
                    : 0;
                return LoadProductsResult.builder()
                    .productId(productInfo.getProductId())
                    .name(productInfo.getName())
                    .documentCount(documentCount)
                    .status(DocumentStatus.PENDING_VALUE)
                    .message(MSG_PRODUCT_LOADED)
                    .traceId(traceId)
                    .processedAt(Instant.now())
                    .success(true)
                    .build();
            }))
            .doOnNext(result -> log.info("Product {} loaded with {} documents",
                result.getProductId(), result.getDocumentCount()));
    }

    private Flux<ProductDocumentToProcess> createDocumentsFlux(ProductInfo productInfo, String traceId) {
        return Flux.fromIterable(productInfo.getDocuments())
            .flatMap(docInfo -> {
                if (docInfo.isZipArchive()) {
                    return expandZipDocument(productInfo.getProductId(), docInfo, traceId);
                } else {
                    return Flux.just(createProductDocument(productInfo.getProductId(), docInfo, null, traceId));
                }
            });
    }

    private Flux<ProductDocumentToProcess> expandZipDocument(String productId, ProductDocumentInfo docInfo, String traceId) {
        if (docInfo.content() == null || docInfo.content().length == 0) {
            log.warn("ZIP document {} has no content, skipping", docInfo.documentId());
            return Flux.empty();
        }

        try {
            ZipArchive zipArchive = ZipArchive.builder()
                .zipContent(docInfo.content())
                .originalFilename(docInfo.filename())
                .build();

            var extractedDocs = zipArchive.extractDocuments();

            if (extractedDocs.isEmpty()) {
                log.warn("ZIP archive {} is empty, skipping", docInfo.filename());
                return Flux.empty();
            }

            log.info("ZIP archive {} contains {} documents", docInfo.filename(), extractedDocs.size());

            return Flux.fromIterable(extractedDocs)
                .map(extracted -> {
                    String childDocId = docInfo.documentId() + "_" + extracted.getFilename();
                    return ProductDocumentToProcess.builder()
                        .documentId(childDocId)
                        .productId(productId)
                        .parentDocumentId(docInfo.documentId())
                        .filename(extracted.getFilename())
                        .content(extracted.getContent())
                        .contentType(extracted.getContentType())
                        .origin(docInfo.origin())
                        .status(DocumentStatus.PENDING_VALUE)
                        .createdAt(Instant.now())
                        .build();
                });
        } catch (IOException e) {
            log.error("Failed to extract ZIP {}: {}", docInfo.filename(), e.getMessage());
            return Flux.empty();
        }
    }

    private ProductDocumentToProcess createProductDocument(String productId, ProductDocumentInfo docInfo,
                                                          String parentId, String traceId) {
        return ProductDocumentToProcess.builder()
            .documentId(docInfo.documentId())
            .productId(productId)
            .parentDocumentId(parentId)
            .filename(docInfo.filename())
            .content(docInfo.content())
            .contentType(docInfo.contentType())
            .origin(docInfo.origin())
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .build();
    }
}
