package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.entity.ProductStatus;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentSkipHandlerTest {

    @Mock
    private ProductDocumentRepository documentRepository;

    @Mock
    private ProductRepository productRepository;

    private ProductStatusAggregator statusAggregator;
    private DocumentSkipHandler skipHandler;

    @BeforeEach
    void setUp() {
        statusAggregator = new ProductStatusAggregator(documentRepository, productRepository);
        skipHandler = new DocumentSkipHandler(documentRepository, statusAggregator);
    }

    @Test
    void skipDocument_shouldUpdateStatusAndReturnSuccessResult() {
        ProductDocumentToProcess doc = createDocument("doc-1", "prod-1");
        String traceId = "trace-123";
        String status = DocumentStatus.SKIPPED.name();
        String message = "Document skipped: folder rule";
        String errorCode = "SKIPPED_FOLDER";
        String externalRef = "doc-1";

        when(documentRepository.updateStatus(eq("doc-1"), eq(status), eq(traceId), isNull(), eq(errorCode)))
            .thenReturn(Mono.empty());
        when(documentRepository.findByProductId(eq("prod-1")))
            .thenReturn(reactor.core.publisher.Flux.just(doc));
        when(productRepository.updateStatus(eq("prod-1"), anyString(), eq(traceId)))
            .thenReturn(Mono.empty());

        StepVerifier.create(skipHandler.skipDocument(doc, traceId, status, message, errorCode, externalRef))
            .expectNextMatches(result -> {
                assertEquals(status, result.getStatus());
                assertEquals(message, result.getMessage());
                assertEquals(traceId, result.getTraceId());
                assertEquals(externalRef, result.getExternalReference());
                assertTrue(result.isSuccess());
                assertNotNull(result.getProcessedAt());
                return true;
            })
            .verifyComplete();

        verify(documentRepository).updateStatus("doc-1", status, traceId, null, errorCode);
    }

    @Test
    void skipDocument_shouldHandleNotSentStatus() {
        ProductDocumentToProcess doc = createDocument("doc-2", "prod-2");
        String traceId = "trace-456";
        String status = DocumentStatus.NOT_SENT.name();
        String message = "Document not sent: origin does not match";
        String errorCode = "NOT_SENT_ORIGIN";
        String externalRef = "doc-2";

        when(documentRepository.updateStatus(any(), any(), any(), any(), any()))
            .thenReturn(Mono.empty());
        when(documentRepository.findByProductId(any()))
            .thenReturn(reactor.core.publisher.Flux.just(doc));
        when(productRepository.updateStatus(any(), any(), any()))
            .thenReturn(Mono.empty());

        StepVerifier.create(skipHandler.skipDocument(doc, traceId, status, message, errorCode, externalRef))
            .expectNextMatches(result -> {
                assertEquals(DocumentStatus.NOT_SENT.name(), result.getStatus());
                assertTrue(result.isSuccess());
                return true;
            })
            .verifyComplete();
    }

    @Test
    void skipDocument_shouldCompleteWhenAllOperationsSucceed() {
        ProductDocumentToProcess doc = createDocument("doc-3", "prod-3");
        String traceId = "trace-789";

        when(documentRepository.updateStatus(any(), any(), any(), any(), any()))
            .thenReturn(Mono.empty());
        when(documentRepository.findByProductId(any()))
            .thenReturn(reactor.core.publisher.Flux.just(doc));
        when(productRepository.updateStatus(any(), any(), any()))
            .thenReturn(Mono.empty());

        StepVerifier.create(skipHandler.skipDocument(doc, traceId,
                DocumentStatus.SKIPPED.name(), "msg", "CODE", "ref"))
            .expectNextMatches(result -> result.isSuccess())
            .verifyComplete();

        verify(documentRepository).updateStatus(any(), any(), any(), any(), any());
    }

    @Test
    void skipDocument_shouldPropagateRepositoryError() {
        ProductDocumentToProcess doc = createDocument("doc-4", "prod-4");
        String traceId = "trace-000";

        when(documentRepository.updateStatus(any(), any(), any(), any(), any()))
            .thenReturn(Mono.error(new RuntimeException("DB error")));

        StepVerifier.create(skipHandler.skipDocument(doc, traceId,
                DocumentStatus.SKIPPED.name(), "msg", "CODE", "ref"))
            .expectErrorMessage("DB error")
            .verify();
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
