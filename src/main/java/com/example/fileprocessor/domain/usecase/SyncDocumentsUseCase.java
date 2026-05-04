package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductHistory;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
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
    private final DocumentRepository documentRepository;
    private final ProductRestGateway productRestGateway;
    private final RulesBussinesGateway documentValidator;

    public Mono<String> execute() {
        log.log(Level.INFO, "Starting document sync");
        return productRepository.findAll()
            .count()
            .flatMap(total -> {
                log.log(Level.INFO, "Found {0} products in database", new Object[]{total});
                return Flux.from(productRepository.findAll())
                    .concatMap(productRestGateway::getDocumentsByProduct)
                    .flatMap(this::processDocument)
                    .then(Mono.just("Document sync completed"));
            })
            .doOnError(e -> log.log(Level.SEVERE, "Document sync failed: " + e.getMessage()));
    }

    private Mono<Void> processDocument(ProductDocumentHistory doc) {
        String productId = doc.productId();
        return documentValidator.validate(doc)
            .flatMap(validated -> saveDocument(validated, productId, null));
    }

    private Mono<Void> saveDocument(ProductDocumentHistory doc, String productId, String parentZipName) {
        Document document = Document.builder()
            .documentId(doc.documentId())
            .productId(productId)
            .name(doc.filename())
            .owner(productId)
            .status(ProductState.PENDING)
            .state(ProductState.SYNCED)
            .isZip(doc.isZip())
            .parentZipName(parentZipName)
            .build();
        return documentRepository.save(document);
    }
}