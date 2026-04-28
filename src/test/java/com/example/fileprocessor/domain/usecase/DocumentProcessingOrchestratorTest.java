package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentProcessingOrchestratorTest {

    @Mock
    private ProductDocumentRepository documentRepository;

    @Mock
    private DocumentProcessingPipeline pipeline;

    private DocumentProcessingOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new DocumentProcessingOrchestrator(
            documentRepository, pipeline, "SOAP");
    }

    @Test
    void executePendingDocuments_shouldProcessClaimedDocuments() {
        ProductDocumentToProcess doc1 = createDocument("doc-1", "prod-1");
        ProductDocumentToProcess doc2 = createDocument("doc-2", "prod-1");

        FileUploadResult result1 = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .correlationId("corr-1")
            .success(true)
            .build();
        FileUploadResult result2 = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .correlationId("corr-2")
            .success(true)
            .build();

        when(documentRepository.findPendingDocuments())
            .thenReturn(Flux.just(doc1, doc2));
        when(documentRepository.claimDocument(anyString()))
            .thenReturn(Mono.just(true));
        when(pipeline.process(any(ProductDocumentToProcess.class), anyString()))
            .thenReturn(Mono.just(result1), Mono.just(result2));

        StepVerifier.create(orchestrator.executePendingDocuments())
            .expectNextMatches(r -> r.getCorrelationId().equals("corr-1"))
            .expectNextMatches(r -> r.getCorrelationId().equals("corr-2"))
            .verifyComplete();

        verify(documentRepository, times(2)).claimDocument(anyString());
        verify(pipeline, times(2)).process(any(ProductDocumentToProcess.class), anyString());
    }

    @Test
    void executePendingDocuments_shouldSkipUnclaimedDocuments() {
        ProductDocumentToProcess doc = createDocument("doc-1", "prod-1");

        when(documentRepository.findPendingDocuments())
            .thenReturn(Flux.just(doc));
        when(documentRepository.claimDocument("doc-1"))
            .thenReturn(Mono.just(false));

        StepVerifier.create(orchestrator.executePendingDocuments())
            .verifyComplete();

        verify(documentRepository).claimDocument("doc-1");
        verify(pipeline, never()).process(any(), anyString());
    }

    @Test
    void executePendingDocuments_shouldSkipWhenNoPendingDocuments() {
        when(documentRepository.findPendingDocuments())
            .thenReturn(Flux.empty());

        StepVerifier.create(orchestrator.executePendingDocuments())
            .verifyComplete();

        verify(documentRepository).findPendingDocuments();
        verify(documentRepository, never()).claimDocument(anyString());
        verify(pipeline, never()).process(any(), anyString());
    }

    @Test
    void executePendingDocuments_shouldPropagatePipelineErrors() {
        ProductDocumentToProcess doc = createDocument("doc-1", "prod-1");

        when(documentRepository.findPendingDocuments())
            .thenReturn(Flux.just(doc));
        when(documentRepository.claimDocument("doc-1"))
            .thenReturn(Mono.just(true));
        when(pipeline.process(any(), anyString()))
            .thenReturn(Mono.error(new RuntimeException("Pipeline error")));

        StepVerifier.create(orchestrator.executePendingDocuments())
            .expectError(RuntimeException.class)
            .verify();
    }

    @Test
    void getImplementationName_shouldReturnConfiguredName() {
        assertEquals("SOAP", orchestrator.getImplementationName());
    }

    private ProductDocumentToProcess createDocument(String docId, String productId) {
        return ProductDocumentToProcess.builder()
            .documentId(docId)
            .productId(productId)
            .filename("file.pdf")
            .content(new byte[]{1})
            .contentType("application/pdf")
            .origin("/incoming/file.pdf")
            .traceId("trace-" + docId)
            .createdAt(Instant.now())
            .build();
    }
}
