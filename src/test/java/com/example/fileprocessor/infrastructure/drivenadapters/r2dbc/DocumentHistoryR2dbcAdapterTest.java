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
import static org.mockito.ArgumentMatchers.anyString;
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
    void findByDocumentId_returnsMappedDomainObjects() {
        DocumentHistoryEntity e1 = entity("doc-1", "PENDING", 0);
        DocumentHistoryEntity e2 = entity("doc-1", "PROCESSED", 0);
        when(springDataRepository.findByDocumentId("doc-1")).thenReturn(Flux.just(e1, e2));

        StepVerifier.create(adapter.findByDocumentId("doc-1"))
            .assertNext(doc -> {
                assertEquals("doc-1", doc.documentId());
                assertEquals("PENDING", doc.state());
            })
            .assertNext(doc -> {
                assertEquals("doc-1", doc.documentId());
                assertEquals("PROCESSED", doc.state());
            })
            .verifyComplete();
    }

    @Test
    void findByDocumentId_whenNotFound_returnsEmptyFlux() {
        when(springDataRepository.findByDocumentId("doc-1")).thenReturn(Flux.empty());

        StepVerifier.create(adapter.findByDocumentId("doc-1"))
            .verifyComplete();
    }

    @Test
    void findByState_delegatesToRepository() {
        DocumentHistoryEntity e1 = entity("doc-1", "PENDING", 0);
        when(springDataRepository.findByState("PENDING")).thenReturn(Flux.just(e1));

        StepVerifier.create(adapter.findByState("PENDING"))
            .assertNext(doc -> assertEquals("PENDING", doc.state()))
            .verifyComplete();
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
    void updateState_mutatesEntityAndSaves() {
        DocumentHistoryEntity existing = entity("doc-1", "PENDING", 0);
        when(springDataRepository.findByDocumentId("doc-1")).thenReturn(Flux.just(existing));
        when(springDataRepository.save(any())).thenReturn(Mono.just(existing));

        StepVerifier.create(adapter.updateState("doc-1", "PROCESSED", "all good"))
            .verifyComplete();

        ArgumentCaptor<DocumentHistoryEntity> captor = ArgumentCaptor.forClass(DocumentHistoryEntity.class);
        verify(springDataRepository).save(captor.capture());
        DocumentHistoryEntity saved = captor.getValue();
        assertEquals("PROCESSED", saved.getState());
        assertEquals("all good", saved.getErrorMessage());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void updateState_whenDocumentNotFound_emitsNothing() {
        when(springDataRepository.findByDocumentId("doc-1")).thenReturn(Flux.empty());

        StepVerifier.create(adapter.updateState("doc-1", "PROCESSED", null))
            .verifyComplete();

        verify(springDataRepository, never()).save(any());
    }

    @Test
    void updateWithAudit_mutatesAllFieldsAndSaves() {
        DocumentHistoryEntity existing = entity("doc-1", "PENDING", 0);
        when(springDataRepository.findByDocumentId("doc-1")).thenReturn(Flux.just(existing));
        when(springDataRepository.save(any())).thenReturn(Mono.just(existing));

        StepVerifier.create(adapter.updateWithAudit("doc-1", "FAILED", "ERR-1", "failure msg", 3, "S3"))
            .verifyComplete();

        ArgumentCaptor<DocumentHistoryEntity> captor = ArgumentCaptor.forClass(DocumentHistoryEntity.class);
        verify(springDataRepository).save(captor.capture());
        DocumentHistoryEntity saved = captor.getValue();
        assertEquals("FAILED", saved.getState());
        assertEquals("ERR-1", saved.getErrorCode());
        assertEquals("failure msg", saved.getErrorMessage());
        assertEquals(3, saved.getRetry());
        assertEquals("S3", saved.getUseCase());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void getRetryCount_whenMultipleEntries_returnsLastRetry() {
        DocumentHistoryEntity e1 = entity("doc-1", "PENDING", 0);
        e1.setRetry(0);
        DocumentHistoryEntity e2 = entity("doc-1", "PENDING", 1);
        e2.setRetry(1);
        DocumentHistoryEntity e3 = entity("doc-1", "PENDING", 2);
        e3.setRetry(2);
        when(springDataRepository.findByDocumentIdAndUseCase("doc-1", "S3"))
            .thenReturn(Flux.just(e1, e2, e3));

        StepVerifier.create(adapter.getRetryCount("doc-1", "S3"))
            .assertNext(count -> assertEquals(2, count))
            .verifyComplete();
    }

    @Test
    void getRetryCount_whenNoEntries_returnsZero() {
        when(springDataRepository.findByDocumentIdAndUseCase("doc-1", "S3"))
            .thenReturn(Flux.empty());

        StepVerifier.create(adapter.getRetryCount("doc-1", "S3"))
            .assertNext(count -> assertEquals(0, count))
            .verifyComplete();
    }

    @Test
    void getRetryCount_whenSingleEntry_returnsItsRetry() {
        DocumentHistoryEntity e = entity("doc-1", "PENDING", 5);
        e.setRetry(5);
        when(springDataRepository.findByDocumentIdAndUseCase("doc-1", "S3"))
            .thenReturn(Flux.just(e));

        StepVerifier.create(adapter.getRetryCount("doc-1", "S3"))
            .assertNext(count -> assertEquals(5, count))
            .verifyComplete();
    }

    @Test
    void findLastAudit_returnsMappedDomain() {
        DocumentHistoryEntity entity = entity("doc-1", "PROCESSED", 0);
        when(springDataRepository.findFirstByDocumentIdOrderByCreatedAtDesc("doc-1"))
            .thenReturn(Mono.just(entity));

        StepVerifier.create(adapter.findLastAudit("doc-1"))
            .assertNext(doc -> {
                assertEquals("doc-1", doc.documentId());
                assertEquals("PROCESSED", doc.state());
            })
            .verifyComplete();
    }

    @Test
    void findLastAudit_whenNotFound_returnsEmptyMono() {
        when(springDataRepository.findFirstByDocumentIdOrderByCreatedAtDesc("doc-1"))
            .thenReturn(Mono.empty());

        StepVerifier.create(adapter.findLastAudit("doc-1"))
            .verifyComplete();
    }
}
