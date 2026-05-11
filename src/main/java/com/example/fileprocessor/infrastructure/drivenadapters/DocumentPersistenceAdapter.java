package com.example.fileprocessor.infrastructure.drivenadapters;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.FinalizeProcessingCommand;
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
    public Mono<Long> resetStaleDocumentsToday(String useCase, LocalDateTime startOfDay, LocalDateTime threshold) {
        return documentRepository.resetStaleDocumentsToday(useCase, startOfDay, threshold);
    }

    @Override
    public Flux<Document> findPendingDocumentsToday(String useCase, LocalDateTime startOfDay) {
        return documentRepository.findByStateAndUseCaseToday(ProductState.PENDING, useCase, startOfDay);
    }

    @Override
    public Mono<Long> lockDocumentForProcessing(Long docId, int currentRetryCount) {
        return documentRepository.updateStateAndRetry(docId, ProductState.PENDING, ProductState.IN_PROGRESS, currentRetryCount, LocalDateTime.now());
    }

    @Override
    public Mono<FileUploadResponse> finalizeProcessingAtomically(FinalizeProcessingCommand command) {
        Mono<FileUploadResponse> combinedOperation = historyRepository.saveHistory(command)
                .then(documentRepository.updateStateAndRetry(
                        command.document().getId(), ProductState.IN_PROGRESS, command.finalState(), command.nextRetryCount(),
                        LocalDateTime.now()))
                .thenReturn(command.response());

        return combinedOperation.as(transactionalOperator::transactional);
    }
}
