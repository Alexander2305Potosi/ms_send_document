package com.example.fileprocessor.infrastructure.drivenadapters;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.DocumentHistoryR2dbcAdapter;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.DocumentR2dbcAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

class DocumentPersistenceAdapterTest {

    private DocumentR2dbcAdapter documentRepository;
    private DocumentHistoryR2dbcAdapter historyRepository;
    private TransactionalOperator transactionalOperator;
    private DocumentPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DocumentR2dbcAdapter.class);
        historyRepository = mock(DocumentHistoryR2dbcAdapter.class);
        transactionalOperator = mock(TransactionalOperator.class);
        
        // Mock transactional operator to pass through the publisher
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(transactionalOperator.transactional(any(Flux.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        adapter = new DocumentPersistenceAdapter(documentRepository, historyRepository, transactionalOperator);
    }

    @Test
    void testFindPendingDocumentsToday() {
        LocalDateTime now = LocalDateTime.now();
        Document doc = Document.builder().build();
        when(documentRepository.findByStatesAndUseCaseToday(any(), eq("SOAP"), eq(now)))
                .thenReturn(Flux.just(doc));

        StepVerifier.create(adapter.findPendingDocumentsToday("SOAP", now))
                .expectNext(doc)
                .expectComplete()
                .verify();

        verify(documentRepository, times(1)).findByStatesAndUseCaseToday(any(), eq("SOAP"), eq(now));
    }

    @Test
    void testLockDocumentForProcessing() {
        Document doc = Document.builder().id(1L).build();
        when(documentRepository.updateStateAndRetry(any(), eq("PENDING"), eq("IN_PROGRESS")))
                .thenReturn(Mono.just(1L));

        StepVerifier.create(adapter.lockDocumentForProcessing(doc, 2))
                .expectNext(1L)
                .expectComplete()
                .verify();

        verify(documentRepository, times(1)).updateStateAndRetry(doc, "PENDING", "IN_PROGRESS");
    }

    @Test
    void testFinalizeProcessingAtomicallyForZip() {
        DocumentHistoryDTO history = DocumentHistoryDTO.builder()
                .documentId(1L)
                .isZip(true)
                .state("PROCESSED")
                .build();

        when(documentRepository.updateStateAndRetry(any(), eq("IN_PROGRESS"))).thenReturn(Mono.just(1L));

        StepVerifier.create(adapter.finalizeProcessingAtomically(history))
                .expectComplete()
                .verify();

        verify(documentRepository, times(1)).updateStateAndRetry(any(), eq("IN_PROGRESS"));
        verify(historyRepository, never()).saveHistory(any());
    }

    @Test
    void testFinalizeProcessingAtomicallyForNonZip() {
        DocumentHistoryDTO history = DocumentHistoryDTO.builder()
                .documentId(1L)
                .isZip(false)
                .state("PROCESSED")
                .build();

        when(documentRepository.updateStateAndRetry(any(), eq("IN_PROGRESS"))).thenReturn(Mono.just(1L));
        when(historyRepository.saveHistory(any())).thenReturn(Mono.empty());

        StepVerifier.create(adapter.finalizeProcessingAtomically(history))
                .expectComplete()
                .verify();

        verify(documentRepository, times(1)).updateStateAndRetry(any(), eq("IN_PROGRESS"));
        verify(historyRepository, times(1)).saveHistory(history);
    }

    @Test
    void testSaveHistory() {
        DocumentHistoryDTO history = DocumentHistoryDTO.builder().build();
        when(historyRepository.saveHistory(any())).thenReturn(Mono.empty());

        StepVerifier.create(adapter.saveHistory(history))
                .expectComplete()
                .verify();

        verify(historyRepository, times(1)).saveHistory(history);
    }
}
