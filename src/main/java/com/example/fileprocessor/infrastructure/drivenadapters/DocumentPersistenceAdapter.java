package com.example.fileprocessor.infrastructure.drivenadapters;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.DocumentUpdateCommand;
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
        // We use the ID directly here, but we could wrap it in a placeholder Document if needed.
        // For simplicity, let's just create a minimal command here.
        DocumentUpdateCommand lockCommand = new DocumentUpdateCommand(
            Document.builder().id(docId).build(),
            ProductState.PENDING, ProductState.IN_PROGRESS, currentRetryCount, null, null
        );
        return documentRepository.updateStateAndRetry(lockCommand);
    }

    @Override
    public Mono<FileUploadResponse> finalizeProcessingAtomically(DocumentUpdateCommand command) {
        Mono<FileUploadResponse> combinedOperation = historyRepository.saveHistory(command)
                .then(documentRepository.updateStateAndRetry(command))
                .thenReturn(command.response());

        return combinedOperation.as(transactionalOperator::transactional);
    }
}
