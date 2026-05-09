package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentHistoryR2dbcAdapterTest {

    @Mock
    private DocumentHistoryRepository springDataRepository;

    private DocumentHistoryR2dbcAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DocumentHistoryR2dbcAdapter(springDataRepository);
    }

    @Test
    void saveHistory_mapsResponseToEntityCorrectly() {
        FileUploadResponse response = FileUploadResponse.builder()
            .status(DocumentStatus.SUCCESS.name())
            .success(true)
            .correlationId("corr-123")
            .message("OK")
            .build();

        when(springDataRepository.save(any(DocumentHistoryEntity.class)))
            .thenReturn(Mono.just(new DocumentHistoryEntity()));

        StepVerifier.create(adapter.saveHistory(10L, "file.pdf", "TEST", response, Instant.now()))
            .verifyComplete();

        verify(springDataRepository).save(argThat(entity -> 
            "SUCCESS".equals(entity.getResult()) &&
            "corr-123".equals(entity.getMessageId()) &&
            entity.getDocumentId().equals(10L)
        ));
    }
}
