package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.MimeTypeResolver;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.TransactionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbstractDocumentProcessingUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ProductRestGateway productRestGateway;
    @Mock
    private RulesBussinesGateway documentValidator;
    @Mock
    private DocumentHistoryRepository historyRepository;
    @Mock
    private MimeTypeResolver mimeTypeResolver;
    @Mock
    private TransactionHandler transactionHandler;

    private AbstractDocumentProcessingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AbstractDocumentProcessingUseCase(
            documentRepository, productRestGateway, documentValidator, historyRepository, mimeTypeResolver, transactionHandler
        ) {
            @Override
            protected Mono<FileUploadResponse> uploadDocument(com.example.fileprocessor.domain.entity.ProductDocumentHistory doc, String productId, Long docId) {
                return Mono.empty();
            }

            @Override
            protected String implementationName() {
                return "TEST";
            }
        };

        // Mock TransactionHandler to just run the publisher
        lenient().when(transactionHandler.run(any(Mono.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        lenient().when(documentRepository.resetStaleDocumentsToday(anyString(), any(), any()))
            .thenReturn(Mono.just(0L));

        lenient().when(documentRepository.updateStateAndRetry(anyLong(), anyString(), anyString(), anyInt(), any()))
            .thenReturn(Mono.just(1L));

        lenient().when(historyRepository.saveHistory(eq(1L), any(), eq("TEST"), any(FileUploadResponse.class), any(Instant.class)))
            .thenReturn(Mono.empty());
    }

    @Test
    void executePendingDocuments_withTechnicalError_incrementsRetryCount() {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-1")
            .productId("prod-1")
            .name("test.pdf")
            .state(ProductState.PENDING)
            .retryCount(0)
            .build();

        when(documentRepository.findByStateAndUseCaseToday(eq(ProductState.PENDING), eq("TEST"), any()))
            .thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.error(new ProcessingException("GATEWAY_TIMEOUT", "Timeout error", (Throwable) null)));

        StepVerifier.create(useCase.executePendingDocuments())
            .assertNext(result -> {
                assertEquals(DocumentStatus.FAILURE.name(), result.getStatus());
                assertEquals("GATEWAY_TIMEOUT", result.getErrorCode());
            })
            .verifyComplete();

        // Verify retry count incremented in repository call
        verify(documentRepository).updateStateAndRetry(eq(1L), eq(ProductState.IN_PROGRESS), eq(ProductState.PENDING), eq(1), any());
    }

    @Test
    void executePendingDocuments_withMaxRetriesReached_marksAsFailed() {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-1")
            .productId("prod-1")
            .name("test.pdf")
            .state(ProductState.PENDING)
            .retryCount(3)
            .build();

        when(documentRepository.findByStateAndUseCaseToday(eq(ProductState.PENDING), eq("TEST"), any()))
            .thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.error(new ProcessingException("GATEWAY_TIMEOUT", "Final timeout", (Throwable) null)));

        StepVerifier.create(useCase.executePendingDocuments())
            .assertNext(result -> {
                assertEquals(DocumentStatus.FAILURE.name(), result.getStatus());
            })
            .verifyComplete();

        // Verify state changed to FAILED because retryCount was already 3
        verify(documentRepository).updateStateAndRetry(eq(1L), eq(ProductState.IN_PROGRESS), eq(ProductState.FAILED), eq(3), any());
    }
}
