package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    private static DocumentHistoryEntity entity(Long docId, String operation, String result, Integer retry) {
        return DocumentHistoryEntity.builder()
            .id(1L)
            .documentId(docId)
            .filename("test.pdf")
            .operation(operation)
            .result(result)
            .retry(retry)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private static DocumentHistory history(Long docId, String filename) {
        return DocumentHistory.builder()
            .documentId(docId)
            .filename(filename)
            .operation("SYNC")
            .result("SUCCESS")
            .retry(0)
            .build();
    }

    @Test
    void save_delegatesToRepository() {
        when(springDataRepository.save(any())).thenReturn(Mono.just(entity(10L, "SYNC", "SUCCESS", 0)));

        StepVerifier.create(adapter.save(history(10L, "test.pdf")))
            .verifyComplete();

        verify(springDataRepository).save(any(DocumentHistoryEntity.class));
    }

    @Test
    void findLastAudit_delegatesToRepository() {
        DocumentHistoryEntity e1 = entity(10L, "SOAP", "FAILURE", 1);
        when(springDataRepository.findLastByDocumentoIdAndOperacionOrderByFechaCreacionDesc(10L, "SOAP"))
            .thenReturn(Mono.just(e1));

        StepVerifier.create(adapter.findLastAudit(10L, "SOAP"))
            .assertNext(doc -> {
                assertEquals(10L, doc.documentId());
                assertEquals("SOAP", doc.operation());
                assertEquals("FAILURE", doc.result());
                assertEquals(1, doc.retry());
            })
            .verifyComplete();
    }

    @Test
    void findLastAudit_whenNotFound_returnsEmptyMono() {
        when(springDataRepository.findLastByDocumentoIdAndOperacionOrderByFechaCreacionDesc(10L, "SOAP"))
            .thenReturn(Mono.empty());

        StepVerifier.create(adapter.findLastAudit(10L, "SOAP"))
            .verifyComplete();
    }
}
