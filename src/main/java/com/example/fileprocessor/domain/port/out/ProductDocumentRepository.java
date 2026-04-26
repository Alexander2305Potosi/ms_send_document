package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProductDocumentRepository {
    Flux<ProductDocumentToProcess> findPendingDocuments();
    Flux<ProductDocumentToProcess> findPendingDocumentsByProduct(String productId);
    Flux<ProductDocumentToProcess> findByProductId(String productId);
    Mono<Boolean> claimDocument(String documentId);
    Mono<Void> save(ProductDocumentToProcess document);
    Mono<Void> saveAll(Flux<ProductDocumentToProcess> documents);
    Mono<Void> updateStatus(String documentId, String status, String traceId,
                            String soapCorrelationId, String errorCode);
}
