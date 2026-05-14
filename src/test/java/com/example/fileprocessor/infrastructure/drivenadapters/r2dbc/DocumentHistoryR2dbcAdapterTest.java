package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.DocumentUpdateCommand;
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
    void saveHistory_withSuccessfulResponse_savesCorrectEntity() {
        Document doc = Document.builder()
            .id(1L)
            .documentId("DOC1")
            .productId("PROD1")
            .name("test.pdf")
            .useCase("SOAP")
            .isZip(false)
            .build();

        FileUploadResponse response = FileUploadResponse.builder()
            .success(true)
            .errorCode(null)
            .message("Success message")
            .build();

        DocumentUpdateCommand command = DocumentUpdateCommand.finalize(doc, response, "PROCESSED", 1, Instant.now());

        when(springDataRepository.save(any(DocumentHistoryEntity.class))).thenReturn(Mono.just(new DocumentHistoryEntity()));

        StepVerifier.create(adapter.saveHistory(command))
            .verifyComplete();

        ArgumentCaptor<DocumentHistoryEntity> captor = ArgumentCaptor.forClass(DocumentHistoryEntity.class);
        verify(springDataRepository).save(captor.capture());

        DocumentHistoryEntity saved = captor.getValue();
        assertEquals(1L, saved.getDocumentId());
        assertEquals("SUCCESS", saved.getResult());
        assertEquals(1, saved.getRetry());
        assertEquals("SOAP", saved.getOperation());
    }
}
