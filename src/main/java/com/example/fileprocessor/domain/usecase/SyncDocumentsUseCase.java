package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.logging.Level;

@Component
@Log
@AllArgsConstructor
public class SyncDocumentsUseCase {
    private final ProductRepository productRepository;
    private final DocumentHistoryRepository historyRepository;
    private final ProductRestGateway productRestGateway;

    public Mono<String> execute() {
        log.log(Level.INFO, "Starting document sync");
        return productRepository.findAll()
                .concatMap(productRestGateway::getDocumentsByProduct)
                .flatMap(this::processDocument)
                .then(Mono.just("Document sync completed"))
            .doOnError(e -> log.log(Level.SEVERE, "Document sync failed: " + e.getMessage()));
    }

    private Mono<Void> processDocument(ProductDocumentHistory doc) {
        return saveDocument(doc);
    }

    private Mono<Void> saveDocument(ProductDocumentHistory doc) {
        String zipName = doc.isZip() ? doc.filename() : null;
        DocumentHistory history = DocumentHistory.builder()
            .documentId(doc.documentId())
            .productId(doc.productId())
            .name(doc.filename())
            .owner(doc.productId())
            .state(ProductState.SYNCED)
            .isZip(doc.isZip())
            .parentZipName(zipName)
            .build();
        return historyRepository.save(history);
    }
}
