package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PROCESSED;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SUCCESS;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentHistoryR2dbcAdapterTest {

    @Mock
    private DocumentHistoryRepository springDataRepository;

    @InjectMocks
    private DocumentHistoryR2dbcAdapter adapter;

    @Test
    void saveHistoryWithSuccessfulResponseSavesCorrectEntity() {
        DocumentHistoryDTO historyDTO = DocumentHistoryDTO.builder()
            .documentId(1L)
            .state(PROCESSED.name())
            .useCase("SOAP")
            .retryCount(1)
            .filename("test.pdf")
            .syncStatus(null)
            .syncMessage("Success message")
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .build();

        when(springDataRepository.save(any(DocumentHistoryEntity.class))).thenReturn(Mono.just(new DocumentHistoryEntity()));

        StepVerifier.create(adapter.saveHistory(historyDTO))
            .expectNextCount(1)
            .verifyComplete();

        ArgumentCaptor<DocumentHistoryEntity> captor = ArgumentCaptor.forClass(DocumentHistoryEntity.class);
        verify(springDataRepository).save(captor.capture());

        DocumentHistoryEntity saved = captor.getValue();
        assertEquals(1L, saved.getDocumentId());
        assertEquals(SUCCESS.name(), saved.getResult());
        assertEquals(1, saved.getRetry());
        assertEquals("SOAP", saved.getUseCase());
        assertEquals("test.pdf", saved.getFilename());
    }
}
