package com.example.fileprocessor.infrastructure.drivenadapters;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DocumentPersistenceAdapter implements DocumentPersistenceGateway {

    private final DocumentRepository documentRepository;
    private final DocumentHistoryRepository historyRepository;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Flux<Document> findPendingDocumentsToday(String useCase, LocalDateTime startOfDay) {
        return documentRepository.findByStateAndUseCaseToday(ProductState.PENDING, useCase, startOfDay);
    }

    @Override
    public Mono<Long> lockDocumentForProcessing(Long docId, int currentRetryCount) {
        Document doc = Document.builder()
            .id(docId)
            .state(ProductState.PENDING)
            .retryCount(currentRetryCount)
            .build();
        
        // Final state after lock
        doc.setState(ProductState.IN_PROGRESS);

        DocumentHistoryDTO lockAudit = DocumentHistoryDTO.builder()
            .errorMessage("Iniciando procesamiento del documento")
            .startedAt(java.time.Instant.now())
            .completedAt(java.time.Instant.now())
            .build();

        return historyRepository.saveHistory(doc, lockAudit)
                .then(documentRepository.updateStateAndRetry(doc, ProductState.PENDING))
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Void> finalizeProcessingAtomically(Document doc, DocumentHistoryDTO history) {
        return historyRepository.saveHistory(doc, history)
                .then(documentRepository.updateStateAndRetry(doc, ProductState.IN_PROGRESS))
                .as(transactionalOperator::transactional)
                .then();
    }
}
