package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;
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
class DocumentR2dbcAdapterTest {

    @Mock
    private com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentRepository springDataRepository;

    private DocumentR2dbcAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DocumentR2dbcAdapter(springDataRepository);
    }

    private static DocumentEntity entity(String docId, String state) {
        return DocumentEntity.builder()
            .id(1L)
            .documentId(docId)
            .productId("prod-1")
            .active(true)
            .name("test.pdf")
            .state(state)
            .useCase("SOAP")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private static Document document(String docId) {
        return Document.builder()
            .documentId(docId)
            .productId("prod-1")
            .name("test.pdf")
            .state("PENDING")
            .useCase("SOAP")
            .build();
    }

    @Test
    void save_delegatesToRepositoryAndReturnsDomain() {
        DocumentEntity savedEntity = entity("doc-1", "PENDING");
        when(springDataRepository.save(any())).thenReturn(Mono.just(savedEntity));

        StepVerifier.create(adapter.save(document("doc-1")))
            .assertNext(result -> {
                assertEquals("doc-1", result.documentId());
                assertEquals("prod-1", result.productId());
                assertEquals("PENDING", result.state());
            })
            .verifyComplete();

        verify(springDataRepository).save(any(DocumentEntity.class));
    }

    @Test
    void findByStateAndUseCase_delegatesToRepository() {
        DocumentEntity e1 = entity("doc-1", "PENDING");
        when(springDataRepository.findByEstadoAndCasoUsoOrderByFechaCreacionDesc("PENDING", "SOAP"))
            .thenReturn(Flux.just(e1));

        StepVerifier.create(adapter.findByStateAndUseCase("PENDING", "SOAP"))
            .assertNext(doc -> {
                assertEquals("PENDING", doc.state());
                assertEquals("SOAP", doc.useCase());
            })
            .verifyComplete();
    }

    @Test
    void findByStateAndUseCase_whenEmpty_returnsEmptyFlux() {
        when(springDataRepository.findByEstadoAndCasoUsoOrderByFechaCreacionDesc("PENDING", "SOAP"))
            .thenReturn(Flux.empty());

        StepVerifier.create(adapter.findByStateAndUseCase("PENDING", "SOAP"))
            .verifyComplete();
    }

    @Test
    void updateStateById_updatesEntityAndSaves() {
        DocumentEntity existing = entity("doc-1", "PENDING");
        LocalDateTime updatedAt = LocalDateTime.now();
        when(springDataRepository.findById(1L)).thenReturn(Mono.just(existing));
        when(springDataRepository.save(any())).thenReturn(Mono.just(existing));

        StepVerifier.create(adapter.updateStateById(1L, "IN_PROGRESS", updatedAt))
            .verifyComplete();

        ArgumentCaptor<DocumentEntity> captor = ArgumentCaptor.forClass(DocumentEntity.class);
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
}
