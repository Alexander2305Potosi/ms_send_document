package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.product.maestro.ProductDocumentFile;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbstractDocumentProcessingUseCaseTest {

    @Mock
    private DocumentPersistenceGateway persistencePort;
    @Mock
    private ProductRestGateway productRestGateway;
    @Mock
    private RulesBussinesGateway documentValidator;

    private AbstractDocumentProcessingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AbstractDocumentProcessingUseCase(
            persistencePort, productRestGateway, documentValidator
        ) {
            @Override
            protected Flux<FileUploadResponse> uploadDocument(DocumentHistoryDTO history, Long docId) {
                return Flux.just(FileUploadResponse.builder().success(true).build());
            }

            @Override
            protected String implementationName() {
                return "TEST";
            }
        };

        lenient().when(persistencePort.lockDocumentForProcessing(anyLong(), anyInt()))
            .thenReturn(Mono.just(1L));

        lenient().when(persistencePort.finalizeProcessingAtomically(any()))
            .thenReturn(Mono.empty());
            
        lenient().when(persistencePort.saveHistory(any()))
            .thenReturn(Mono.empty());
    }

    @Test
    void executePendingDocuments_withTechnicalError_incrementsRetryCount() {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-1")
            .productId("prod-1")
            .name("test.pdf")
            .state(ProcessingResultCodes.PENDING.name())
            .retryCount(0)
            .isZip(false)
            .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any()))
            .thenReturn(Flux.just(doc));
        
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.error(new ProcessingException("Timeout error", ProcessingResultCodes.GATEWAY_TIMEOUT.name(), (Throwable) null)));

        StepVerifier.create(useCase.executePendingDocuments()
                .contextWrite(ctx -> ctx.put("message-id", "test-trace-tech-error")))
            .assertNext(result -> {
                assertEquals(ProcessingResultCodes.GATEWAY_TIMEOUT.name(), result.getSyncStatus());
            })
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        verify(persistencePort).finalizeProcessingAtomically(
            argThat(h -> ProcessingResultCodes.PENDING.name().equals(h.getState()) && h.getRetryCount() == 0 
                    && h.getSyncStatus().equals(ProcessingResultCodes.GATEWAY_TIMEOUT.name()))
        );
    }

    @Test
    void executePendingDocuments_withMaxRetriesReached_marksAsFailed() {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-1")
            .productId("prod-1")
            .name("test.pdf")
            .state(ProcessingResultCodes.PENDING.name())
            .retryCount(3) 
            .isZip(false)
            .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any()))
            .thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.error(new ProcessingException("Final timeout", ProcessingResultCodes.GATEWAY_TIMEOUT.name(), (Throwable) null)));

        StepVerifier.create(useCase.executePendingDocuments()
                .contextWrite(ctx -> ctx.put("message-id", "test-trace-max-retries")))
            .expectNextCount(1)
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        verify(persistencePort).finalizeProcessingAtomically(
            argThat(h -> ProcessingResultCodes.FAILED.name().equals(h.getState()) && h.getRetryCount() == 3)
        );
    }

    @Test
    void executePendingDocuments_withSuccess_marksAsProcessed() {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-1")
            .productId("prod-1")
            .name("test.pdf")
            .state(ProcessingResultCodes.PENDING.name())
            .retryCount(0)
            .isZip(false)
            .build();

        ProductDocumentFile file = ProductDocumentFile.builder()
                .productId("prod-1")
                .documentId("doc-1")
                .filename("test.pdf")
                .content(new byte[]{1, 2, 3})
                .size(3L)
                .isZip(false)
                .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any()))
            .thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.just(file));
        
        when(documentValidator.validate(any(DocumentHistoryDTO.class), anyBoolean()))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.executePendingDocuments()
                .contextWrite(ctx -> ctx.put("message-id", "test-trace-success")))
            .expectNextCount(1)
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        ArgumentCaptor<DocumentHistoryDTO> historyCaptor = ArgumentCaptor.forClass(DocumentHistoryDTO.class);
        
        verify(persistencePort).finalizeProcessingAtomically(historyCaptor.capture());
        
        assertEquals(ProcessingResultCodes.PROCESSED.name(), historyCaptor.getValue().getState());
        assertNotNull(historyCaptor.getValue().getCompletedAt());
    }

    @Test
    void executePendingDocuments_withMultipleDocuments_processesAllSequentially() {
        Document doc1 = Document.builder().id(1L).documentId("doc-1").productId("p1").state("PENDING").isZip(false).build();
        Document doc2 = Document.builder().id(2L).documentId("doc-2").productId("p2").state("PENDING").isZip(false).build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any()))
            .thenReturn(Flux.just(doc1, doc2));
        
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.just(ProductDocumentFile.builder().isZip(false).build()));
        
        when(documentValidator.validate(any(), anyBoolean()))
            .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextCount(2)
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        verify(persistencePort, times(2)).finalizeProcessingAtomically(any());
    }

    @Test
    void executePendingDocuments_withTechnicalRetry_savesAuditOnly() {
        Document doc = Document.builder().id(1L).documentId("doc-1").productId("p1").state("PENDING").isZip(false).retryCount(0).build();
        
        when(persistencePort.findPendingDocumentsToday(anyString(), any()))
            .thenReturn(Flux.just(doc));
        
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.just(ProductDocumentFile.builder().isZip(false).build()));
        
        when(documentValidator.validate(any(), anyBoolean()))
            .thenReturn(Mono.just(DocumentHistoryDTO.builder().build()));

        // We need to override uploadDocument to return a technical retry response
        useCase = new AbstractDocumentProcessingUseCase(persistencePort, productRestGateway, documentValidator) {
            @Override
            protected Flux<FileUploadResponse> uploadDocument(DocumentHistoryDTO history, Long docId) {
                return Flux.just(FileUploadResponse.builder()
                        .success(false)
                        .technicalRetry(true)
                        .attemptCount(1)
                        .syncStatus(ProcessingResultCodes.GATEWAY_TIMEOUT.name())
                        .message("Technical error")
                        .build());
            }
            @Override protected String implementationName() { return "TEST"; }
        };

        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextCount(1)
            .expectComplete()
            .verify(Duration.ofSeconds(5));

        verify(persistencePort).saveHistory(any());
        verify(persistencePort, never()).finalizeProcessingAtomically(any());
    }

    @Test
    void executePendingDocuments_withValidationError_marksAsFailed() {
        Document doc = Document.builder().id(1L).documentId("doc-1").productId("p1").state("PENDING").isZip(false).build();
        
        when(persistencePort.findPendingDocumentsToday(anyString(), any()))
            .thenReturn(Flux.just(doc));
        
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.just(ProductDocumentFile.builder().isZip(false).build()));
        
        when(documentValidator.validate(any(), anyBoolean()))
            .thenReturn(Mono.error(new ProcessingException("Rules failed", ProcessingResultCodes.PATTERN_MISMATCH.name())));

        StepVerifier.create(useCase.executePendingDocuments())
            .assertNext(resp -> {
                assertFalse(resp.isSuccess());
                assertEquals(ProcessingResultCodes.PATTERN_MISMATCH.name(), resp.getSyncStatus());
            })
            .expectComplete()
            .verify(Duration.ofSeconds(5));

        verify(persistencePort).finalizeProcessingAtomically(argThat(h -> 
            ProcessingResultCodes.FAILED.name().equals(h.getState()) &&
            ProcessingResultCodes.PATTERN_MISMATCH.name().equals(h.getSyncStatus())
        ));
    }
}
