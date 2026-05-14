package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentRepository;
import org.reactivecommons.utils.ObjectMapper;
import org.reactivecommons.utils.ObjectMapperImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentR2dbcAdapterTest {

    @Mock
    private DocumentRepository springDataRepository;

    private final ObjectMapper objectMapper = new ObjectMapperImp();
    private DocumentR2dbcAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DocumentR2dbcAdapter(springDataRepository, objectMapper);
    }

    @Test
    void save_delegatesToSpringDataRepository() {
        Document domain = Document.builder().id(1L).name("test.pdf").build();
        DocumentEntity entity = DocumentEntity.builder().id(1L).name("test.pdf").build();

        when(springDataRepository.save(any())).thenReturn(Mono.just(entity));

        StepVerifier.create(adapter.save(domain))
            .assertNext(res -> {
                assertEquals(1L, res.getId());
                assertEquals("test.pdf", res.getName());
            })
            .verifyComplete();
    }

    @Test
    void findByStateAndUseCaseToday_delegatesToSpringDataRepository() {
        LocalDateTime startOfDay = LocalDateTime.of(2026, 5, 9, 0, 0);
        DocumentEntity entity = DocumentEntity.builder().id(1L).state("PENDING").useCase("SYNC").build();

        when(springDataRepository.findByStateAndUseCaseToday("PENDING", "SYNC", startOfDay))
            .thenReturn(Flux.just(entity));

        StepVerifier.create(adapter.findByStateAndUseCaseToday("PENDING", "SYNC", startOfDay))
            .assertNext(doc -> {
                assertEquals(1L, doc.getId());
                assertEquals("PENDING", doc.getState());
                assertEquals("SYNC", doc.getUseCase());
            })
            .verifyComplete();

        verify(springDataRepository).findByStateAndUseCaseToday("PENDING", "SYNC", startOfDay);
    }

    @Test
    void updateStateAndRetry_delegatesToSpringDataRepository() {
        Document domain = Document.builder()
            .id(1L)
            .state("PROCESSED")
            .retryCount(1)
            .errorMessage("Success")
            .build();
            
        DocumentEntity entity = DocumentEntity.builder()
            .id(1L)
            .state("IN_PROGRESS") // Initial state in DB
            .build();

        when(springDataRepository.findById(1L)).thenReturn(Mono.just(entity));
        when(springDataRepository.save(any())).thenReturn(Mono.just(entity));

        StepVerifier.create(adapter.updateStateAndRetry(domain, "IN_PROGRESS"))
            .expectNext(1L)
            .verifyComplete();

        verify(springDataRepository).findById(1L);
        verify(springDataRepository).save(argThat(e -> 
            "PROCESSED".equals(e.getState()) && 
            "Success".equals(e.getErrorMessage())
        ));
    }

    @Test
    void existsByProductIdAndDocumentId_delegatesToSpringDataRepository() {
        when(springDataRepository.existsByProductIdAndDocumentId("PROD-1", "DOC-1"))
            .thenReturn(Mono.just(true));

        StepVerifier.create(adapter.existsByProductIdAndDocumentId("PROD-1", "DOC-1"))
            .expectNext(true)
            .verifyComplete();

        verify(springDataRepository).existsByProductIdAndDocumentId("PROD-1", "DOC-1");
    }
}
