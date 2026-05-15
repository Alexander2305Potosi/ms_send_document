package com.example.fileprocessor.infrastructure.drivenadapters;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.DocumentHistoryR2dbcAdapter;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.DocumentR2dbcAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.transaction.reactive.TransactionalOperator;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DocumentPersistenceAdapter implements DocumentPersistenceGateway {

    private final DocumentR2dbcAdapter documentRepository;
    private final DocumentHistoryR2dbcAdapter historyRepository;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Flux<Document> findPendingDocumentsToday(String useCase, LocalDateTime startOfDay) {
        return documentRepository.findByStateAndUseCaseToday(ProcessingResultCodes.PENDING.name(), useCase, startOfDay);
    }

    @Override
    public Mono<Long> lockDocumentForProcessing(Long id, int currentRetry) {
        Document doc = new Document();
        doc.setId(id);
        doc.setState(ProcessingResultCodes.IN_PROGRESS.name());
        
        return documentRepository.updateStateAndRetry(doc, ProcessingResultCodes.PENDING.name());
    }

    @Override
    public Mono<Void> finalizeProcessingAtomically(DocumentHistoryDTO history, int businessRetryCount) {
        String initialState = ProcessingResultCodes.IN_PROGRESS.name();
        
        Document doc = Document.builder()
                .id(history.getDocumentId())
                .state(history.getState())
                .retryCount(businessRetryCount)
                .errorMessage(history.getErrorMessage())
                .build();

        return documentRepository.updateStateAndRetry(doc, initialState)
                .then(historyRepository.saveHistory(history))
                .as(transactionalOperator::transactional)
                .then();
    }

    @Override
    public Mono<Void> saveHistory(DocumentHistoryDTO history) {
        return historyRepository.saveHistory(history).then();
    }
}
