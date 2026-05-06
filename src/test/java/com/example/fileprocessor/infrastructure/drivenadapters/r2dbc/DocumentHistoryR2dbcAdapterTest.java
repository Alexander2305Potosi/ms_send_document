package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentHistoryR2dbcAdapterTest {

    @Mock
    private com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentHistoryRepository springDataRepository;

    private DocumentHistoryR2dbcAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DocumentHistoryR2dbcAdapter(springDataRepository);
    }

    private static DocumentHistoryEntity entity(String docId, String state, Integer retry) {
        return DocumentHistoryEntity.builder()
            .id(1L)
            .documentId(docId)
            .productId("prod-1")
            .active(true)
            .name("test.pdf")
            .state(state)
            .retry(retry)
            .useCase("retention")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private static DocumentHistory history(String docId) {
        return DocumentHistory.builder()
            .documentId(docId)
            .productId("prod-1")
            .name("test.pdf")
            .state("PENDING")
            .retry(0)
            .build();
    }

    @Test
    void save_delegatesToRepository() {
        when(springDataRepository.save(any())).thenReturn(Mono.just(entity("doc-1", "PENDING", 0)));

        StepVerifier.create(adapter.save(history("doc-1")))
            .verifyComplete();

        verify(springDataRepository).save(any(DocumentHistoryEntity.class));
    }

    @Test
    void findByStateAndUseCase_delegatesToRepository() {
        DocumentHistoryEntity e1 = entity("doc-1", "PENDING", 0);
        when(springDataRepository.findByEstadoAndCasoUsoOrderByFechaCreacionDesc("PENDING", "retention"))
            .thenReturn(Flux.just(e1));

        StepVerifier.create(adapter.findByStateAndUseCase("PENDING", "retention"))
            .assertNext(doc -> {
                assertEquals("PENDING", doc.state());
                assertEquals("retention", doc.useCase());
            })
            .verifyComplete();
    }

    @Test
    void findLastAudit_delegatesToRepository() {
        DocumentHistoryEntity e1 = entity("doc-1", "PROCESSED", 0);
        when(springDataRepository.findLastByDocumentIdAndCasoUsoOrderByFechaCreacionDesc("doc-1", "retention"))
            .thenReturn(Mono.just(e1));

        StepVerifier.create(adapter.findLastAudit("doc-1", "retention"))
            .assertNext(doc -> {
                assertEquals("doc-1", doc.documentId());
                assertEquals("PROCESSED", doc.state());
                assertEquals("retention", doc.useCase());
            })
            .verifyComplete();
    }

    @Test
    void findLastAudit_whenNotFound_returnsEmptyMono() {
        when(springDataRepository.findLastByDocumentIdAndCasoUsoOrderByFechaCreacionDesc("doc-1", "retention"))
            .thenReturn(Mono.empty());

        StepVerifier.create(adapter.findLastAudit("doc-1", "retention"))
            .verifyComplete();
    }

    @Test
    void updateStateById_updatesEntityAndSaves() {
        DocumentHistoryEntity existing = entity("doc-1", "PENDING", 0);
        LocalDateTime updatedAt = LocalDateTime.now();
        when(springDataRepository.findById(1L)).thenReturn(Mono.just(existing));
        when(springDataRepository.save(any())).thenReturn(Mono.just(existing));

        StepVerifier.create(adapter.updateStateById(1L, "IN_PROGRESS", updatedAt))
            .verifyComplete();

        ArgumentCaptor<DocumentHistoryEntity> captor = ArgumentCaptor.forClass(DocumentHistoryEntity.class);
        verify(springDataRepository).save(captor.capture());
        assertEquals("IN_PROGRESS", captor.getValue().getState());
        assertEquals(updatedAt, captor.getValue().getUpdatedAt());
    }

    @Test
    void updateStateById_whenDocumentNotFound_emitsNothing() {
        when(springDataRepository.findById(999L)).thenReturn(Mono.empty());

        StepVerifier.create(adapter.updateStateById(999L, "IN_PROGRESS", LocalDateTime.now()))
            .verifyComplete();

        verify(springDataRepository, never()).save(any());
    }

    @Test
    void updateWithAuditById_mutatesAllFieldsAndSaves() {
        DocumentHistoryEntity existing = entity("doc-1", "PENDING", 0);
        LocalDateTime completedAt = LocalDateTime.now();
        when(springDataRepository.findById(1L)).thenReturn(Mono.just(existing));
        when(springDataRepository.save(any())).thenReturn(Mono.just(existing));

        StepVerifier.create(adapter.updateWithAuditById(
            1L, "FAILED", "ERR-1", "failure msg", 3, "stacktrace\nline2", completedAt))
            .verifyComplete();

        ArgumentCaptor<DocumentHistoryEntity> captor = ArgumentCaptor.forClass(DocumentHistoryEntity.class);
        verify(springDataRepository).save(captor.capture());
        DocumentHistoryEntity saved = captor.getValue();
        assertEquals("FAILED", saved.getState());
        assertEquals("ERR-1", saved.getErrorCode());
        assertEquals("failure msg", saved.getErrorMessage());
        assertEquals(3, saved.getRetry());
        assertEquals("stacktrace\nline2", saved.getStackTrace());
        assertEquals(completedAt, saved.getCompletedAt());
    }
}