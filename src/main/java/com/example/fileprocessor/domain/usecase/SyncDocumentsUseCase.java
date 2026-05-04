package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.util.ZipDecompressor;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Log
@AllArgsConstructor
public class SyncDocumentsUseCase {
    private final DocumentRepository documentRepository;
    private final ProductRestGateway productRestGateway;
    private final RulesBussinesGateway documentValidator;

    public Mono<Void> execute() {
        log.info("Starting document sync");
        return productRestGateway.getAllProducts()
            .flatMap(this::processDocument)
            .then()
            .doOnTerminate(() -> log.info("Document sync completed"))
            .doOnError(e -> log.severe("Document sync failed: " + e.getMessage()));
    }

    private Mono<Void> processDocument(ProductDocumentHistory doc) {
        String productId = doc.productId();
        if (doc.isZip()) {
            return saveDocument(doc, productId, null)
                .thenMany(ZipDecompressor.decompress(doc))
                .flatMap(decompressed ->
                    documentValidator.validate(decompressed)
                        .flatMap(validated -> saveDocument(validated, productId, doc.filename())))
                .then();
        } else {
            return documentValidator.validate(doc)
                .flatMap(validated -> saveDocument(validated, productId, null));
        }
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