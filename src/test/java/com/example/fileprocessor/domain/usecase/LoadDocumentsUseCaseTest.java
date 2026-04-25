package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentInfo;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.DocumentToProcess;
import com.example.fileprocessor.domain.port.out.DocumentRestGateway;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoadDocumentsUseCaseTest {

    @Mock
    private DocumentRestGateway documentGateway;

    @Mock
    private DocumentRepository documentRepository;

    private LoadDocumentsUseCase loadDocumentsUseCase;

    @BeforeEach
    void setUp() {
        loadDocumentsUseCase = new LoadDocumentsUseCase(documentGateway, documentRepository);
    }

    @Test
    void execute_shouldLoadAllDocuments() {
        DocumentInfo doc1 = DocumentInfo.builder()
            .documentId("doc-001")
            .filename("test.pdf")
            .content(new byte[1024])
            .contentType("application/pdf")
            .size(1024)
            .isZip(false)
            .origin("folderA/incoming")
            .build();

        DocumentInfo doc2 = DocumentInfo.builder()
            .documentId("doc-002")
            .filename("test.docx")
            .content(new byte[2048])
            .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            .size(2048)
            .isZip(false)
            .origin("folderB/incoming")
            .build();

        when(documentGateway.getAllDocuments(any())).thenReturn(Flux.just(doc1, doc2));
        when(documentRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(loadDocumentsUseCase.execute())
            .expectNextCount(2)
            .verifyComplete();

        verify(documentRepository, times(2)).save(any());
    }

    @Test
    void execute_shouldSaveDocumentWithPendingStatus() {
        DocumentInfo doc = DocumentInfo.builder()
            .documentId("doc-001")
            .filename("test.pdf")
            .content(new byte[1024])
            .contentType("application/pdf")
            .size(1024)
            .isZip(false)
            .origin("folderA/incoming")
            .build();

        when(documentGateway.getAllDocuments(any())).thenReturn(Flux.just(doc));
        when(documentRepository.save(any())).thenReturn(Mono.empty());

        loadDocumentsUseCase.execute().collectList().block();

        ArgumentCaptor<DocumentToProcess> captor = ArgumentCaptor.forClass(DocumentToProcess.class);
        verify(documentRepository).save(captor.capture());

        DocumentToProcess saved = captor.getValue();
        assertEquals("doc-001", saved.getDocumentId());
        assertEquals("test.pdf", saved.getFilename());
        assertEquals("folderA/incoming", saved.getOrigin());
        assertEquals(DocumentStatus.PENDING_VALUE, saved.getStatus());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void execute_shouldHandleSaveError() {
        DocumentInfo doc = DocumentInfo.builder()
            .documentId("doc-001")
            .filename("test.pdf")
            .content(new byte[1024])
            .contentType("application/pdf")
            .size(1024)
            .isZip(false)
            .origin("folderA/incoming")
            .build();

        when(documentGateway.getAllDocuments(any())).thenReturn(Flux.just(doc));
        when(documentRepository.save(any())).thenReturn(Mono.error(new RuntimeException("DB error")));

        StepVerifier.create(loadDocumentsUseCase.execute())
            .assertNext(result -> {
                assertEquals("doc-001", result.getDocumentId());
                assertEquals("FAILED", result.getStatus());
                assertFalse(result.isSuccess());
                assertTrue(result.getMessage().contains("DB error"));
            })
            .verifyComplete();
    }

    @Test
    void execute_shouldHandleEmptyList() {
        when(documentGateway.getAllDocuments(any())).thenReturn(Flux.empty());

        StepVerifier.create(loadDocumentsUseCase.execute())
            .verifyComplete();

        verify(documentRepository, never()).save(any());
    }

    @Test
    void execute_shouldHandleOriginNull() {
        DocumentInfo doc = DocumentInfo.builder()
            .documentId("doc-001")
            .filename("test.pdf")
            .content(new byte[1024])
            .contentType("application/pdf")
            .size(1024)
            .isZip(false)
            .origin(null)
            .build();

        when(documentGateway.getAllDocuments(any())).thenReturn(Flux.just(doc));
        when(documentRepository.save(any())).thenReturn(Mono.empty());

        loadDocumentsUseCase.execute().blockLast();

        ArgumentCaptor<DocumentToProcess> captor = ArgumentCaptor.forClass(DocumentToProcess.class);
        verify(documentRepository).save(captor.capture());

        DocumentToProcess saved = captor.getValue();
        assertEquals("", saved.getOrigin());
    }
}
