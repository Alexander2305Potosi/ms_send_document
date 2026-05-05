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
        when(springDataRepository.findByStateAndUseCase("PENDING", "retention")).thenReturn(Flux.just(e1));

        StepVerifier.create(adapter.findByStateAndUseCase("PENDING", "retention"))
            .assertNext(doc -> {
                assertEquals("PENDING", doc.state());
                assertEquals("retention", doc.useCase());
            })
            .verifyComplete();
    }

    @Test
    void findByDocumentIdAndStateAndUseCase_delegatesToRepository() {
        DocumentHistoryEntity e1 = entity("doc-1", "PENDING", 0);
        when(springDataRepository.findByDocumentIdAndStateAndUseCase("doc-1", "PENDING", "retention"))
            .thenReturn(Flux.just(e1));

        StepVerifier.create(adapter.findByDocumentIdAndStateAndUseCase("doc-1", "PENDING", "retention"))
            .assertNext(doc -> {
                assertEquals("doc-1", doc.documentId());
                assertEquals("PENDING", doc.state());
                assertEquals("retention", doc.useCase());
            })
            .verifyComplete();
    }

    @Test
    void updateStateAndUseCase_updatesEntityAndSaves() {
        DocumentHistoryEntity existing = entity("doc-1", "PENDING", 0);
        when(springDataRepository.findByDocumentIdAndStateAndUseCase("doc-1", "PENDING", "retention"))
            .thenReturn(Flux.just(existing));
        when(springDataRepository.save(any())).thenReturn(Mono.just(existing));

        StepVerifier.create(adapter.updateStateAndUseCase("doc-1", "IN_PROGRESS", "retention"))
            .verifyComplete();

        ArgumentCaptor<DocumentHistoryEntity> captor = ArgumentCaptor.forClass(DocumentHistoryEntity.class);
        verify(springDataRepository).save(captor.capture());
        assertNotNull(captor.getValue().getUpdatedAt());
    }

    @Test
    void updateStateAndUseCase_whenDocumentNotFound_emitsNothing() {
        when(springDataRepository.findByDocumentIdAndStateAndUseCase("doc-1", "PENDING", "retention"))
            .thenReturn(Flux.empty());

        StepVerifier.create(adapter.updateStateAndUseCase("doc-1", "IN_PROGRESS", "retention"))
            .verifyComplete();

        verify(springDataRepository, never()).save(any());
    }

    @Test
    void updateWithAudit_mutatesAllFieldsAndSaves() {
        DocumentHistoryEntity existing = entity("doc-1", "PENDING", 0);
        when(springDataRepository.findByDocumentIdAndStateAndUseCase("doc-1", "PENDING", "S3"))
            .thenReturn(Flux.just(existing));
        when(springDataRepository.save(any())).thenReturn(Mono.just(existing));

        StepVerifier.create(adapter.updateWithAudit(
            "doc-1", "FAILED", "ERR-1", "failure msg", 3, "S3", "stacktrace\nline2", LocalDateTime.now()))
            .verifyComplete();

        ArgumentCaptor<DocumentHistoryEntity> captor = ArgumentCaptor.forClass(DocumentHistoryEntity.class);
        verify(springDataRepository).save(captor.capture());
        DocumentHistoryEntity saved = captor.getValue();
        assertEquals("FAILED", saved.getState());
        assertEquals("ERR-1", saved.getErrorCode());
        assertEquals("failure msg", saved.getErrorMessage());
        assertEquals(3, saved.getRetry());
        assertEquals("S3", saved.getUseCase());
        assertEquals("stacktrace\nline2", saved.getStackTrace());
        assertNotNull(saved.getCompletedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void findLastAudit_returnsMappedDomain() {
        DocumentHistoryEntity entity = entity("doc-1", "PROCESSED", 0);
        when(springDataRepository.findFirstByDocumentIdAndUseCaseOrderByCreatedAtDesc("doc-1", "retention"))
            .thenReturn(Mono.just(entity));

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
        when(springDataRepository.findFirstByDocumentIdAndUseCaseOrderByCreatedAtDesc("doc-1", "retention"))
            .thenReturn(Mono.empty());

        StepVerifier.create(adapter.findLastAudit("doc-1", "retention"))
            .verifyComplete();
    }
}