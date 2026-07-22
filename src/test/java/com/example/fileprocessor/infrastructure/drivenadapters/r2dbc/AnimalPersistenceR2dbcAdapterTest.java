package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.animal.AnimalDocument;
import com.example.fileprocessor.domain.entity.animal.AnimalDocumentHistoryDTO;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.AnimalDocumentEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.AnimalDocumentHistoryEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.AnimalDocumentRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.AnimalDocumentHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnimalPersistenceR2dbcAdapterTest {

    private AnimalDocumentRepository documentRepository;
    private AnimalDocumentHistoryRepository historyRepository;
    private TransactionalOperator transactionalOperator;
    private AnimalPersistenceR2dbcAdapter adapter;

    @BeforeEach
    void setUp() {
        documentRepository = mock(AnimalDocumentRepository.class);
        historyRepository = mock(AnimalDocumentHistoryRepository.class);
        transactionalOperator = mock(TransactionalOperator.class);

        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(transactionalOperator.transactional(any(Flux.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        adapter = new AnimalPersistenceR2dbcAdapter(documentRepository, historyRepository, transactionalOperator);
    }

    @Test
    void testFindPendingDocumentsToday() {
        LocalDateTime now = LocalDateTime.now();
        AnimalDocumentEntity entity = AnimalDocumentEntity.builder()
                .id(1L)
                .documentId("doc-1")
                .build();
        when(documentRepository.findByStatesAndUseCaseToday(any(), eq("Animal"), eq(now)))
                .thenReturn(Flux.just(entity));

        StepVerifier.create(adapter.findPendingDocumentsToday("Animal", now))
                .assertNext(doc -> {
                    assertEquals(1L, doc.getId());
                    assertEquals("doc-1", doc.getDocumentId());
                })
                .expectComplete()
                .verify();

        verify(documentRepository, times(1)).findByStatesAndUseCaseToday(any(), eq("Animal"), eq(now));
    }

    @Test
    void testLockDocumentForProcessingWhenExists() {
        Document doc = Document.builder().productId("animal-1").documentId("doc-1").build();
        AnimalDocumentEntity entity = AnimalDocumentEntity.builder().id(10L).documentId("doc-1").state("PENDING").build();

        when(documentRepository.existsByProductIdAndDocumentId("animal-1", "doc-1")).thenReturn(Mono.just(true));
        when(documentRepository.findByStatesAndUseCaseToday(any(), eq("Animal"), any()))
                .thenReturn(Flux.just(entity));
        when(documentRepository.save(any(AnimalDocumentEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(adapter.lockDocumentForProcessing(doc, 2))
                .expectNext(1L)
                .expectComplete()
                .verify();

        assertEquals(10L, doc.getId());
        verify(documentRepository, times(1)).save(any(AnimalDocumentEntity.class));
    }

    @Test
    void testLockDocumentForProcessingWhenDoesNotExist() {
        AnimalDocument doc = AnimalDocument.builder().animalId("animal-1").documentId("doc-1").isZip(false).build();
        AnimalDocumentEntity savedEntity = AnimalDocumentEntity.builder().id(20L).build();

        when(documentRepository.existsByProductIdAndDocumentId("animal-1", "doc-1")).thenReturn(Mono.just(false));
        when(documentRepository.save(any(AnimalDocumentEntity.class))).thenReturn(Mono.just(savedEntity));

        StepVerifier.create(adapter.lockDocumentForProcessing(doc, 2))
                .expectNext(20L)
                .expectComplete()
                .verify();

        assertEquals(20L, doc.getId());
        verify(documentRepository, times(1)).save(any(AnimalDocumentEntity.class));
    }

    @Test
    void testFinalizeProcessingAtomicallyForZip() {
        AnimalDocumentHistoryDTO history = AnimalDocumentHistoryDTO.builder()
                .documentId(1L)
                .isZip(true)
                .state("PROCESSED")
                .build();
        AnimalDocumentEntity entity = AnimalDocumentEntity.builder().id(1L).state("IN_PROGRESS").build();

        when(documentRepository.findById(1L)).thenReturn(Mono.just(entity));
        when(documentRepository.save(any(AnimalDocumentEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(adapter.finalizeProcessingAtomically(history))
                .expectComplete()
                .verify();

        verify(documentRepository, times(1)).save(any(AnimalDocumentEntity.class));
        verify(historyRepository, never()).save(any());
    }

    @Test
    void testFinalizeProcessingAtomicallyForNonZip() {
        AnimalDocumentHistoryDTO history = AnimalDocumentHistoryDTO.builder()
                .documentId(1L)
                .isZip(false)
                .state("PROCESSED")
                .build();
        AnimalDocumentEntity entity = AnimalDocumentEntity.builder().id(1L).state("IN_PROGRESS").build();

        when(documentRepository.findById(1L)).thenReturn(Mono.just(entity));
        when(documentRepository.save(any(AnimalDocumentEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(historyRepository.save(any(AnimalDocumentHistoryEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(adapter.finalizeProcessingAtomically(history))
                .expectComplete()
                .verify();

        verify(documentRepository, times(1)).save(any(AnimalDocumentEntity.class));
        verify(historyRepository, times(1)).save(any(AnimalDocumentHistoryEntity.class));
    }

    @Test
    void testFinalizeProcessingAtomicallyStateMismatch() {
        AnimalDocumentHistoryDTO history = AnimalDocumentHistoryDTO.builder()
                .documentId(1L)
                .state("PROCESSED")
                .build();
        AnimalDocumentEntity entity = AnimalDocumentEntity.builder().id(1L).state("PENDING").build();

        when(documentRepository.findById(1L)).thenReturn(Mono.just(entity));
        when(historyRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(adapter.finalizeProcessingAtomically(history))
                .expectError(com.example.fileprocessor.domain.exception.ProcessingException.class)
                .verify();
    }

    @Test
    void testSaveHistory() {
        AnimalDocumentHistoryDTO history = AnimalDocumentHistoryDTO.builder()
                .documentId(1L)
                .state("PROCESSED")
                .build();
        when(historyRepository.save(any(AnimalDocumentHistoryEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(adapter.saveHistory(history))
                .expectComplete()
                .verify();

        verify(historyRepository, times(1)).save(any(AnimalDocumentHistoryEntity.class));
    }
}
