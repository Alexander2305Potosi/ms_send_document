package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistory;
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
            protected Mono<FileUploadResponse> uploadDocument(DocumentHistory history, Long docId) {
                return Mono.just(FileUploadResponse.builder().success(true).build());
            }

            @Override
            protected String implementationName() {
                return "TEST";
            }
        };

        lenient().when(persistencePort.lockDocumentForProcessing(anyLong(), anyInt()))
            .thenReturn(Mono.just(1L));

        lenient().when(persistencePort.finalizeProcessingAtomically(any(), any()))
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
            .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any()))
            .thenReturn(Flux.just(doc));
        
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.error(new ProcessingException(ProcessingResultCodes.GATEWAY_TIMEOUT.name(), "Timeout error", (Throwable) null)));

        StepVerifier.create(useCase.executePendingDocuments()
                .contextWrite(ctx -> ctx.put("message-id", "test-trace-tech-error")))
            .assertNext(result -> {
                assertEquals(ProcessingResultCodes.GATEWAY_TIMEOUT.name(), result.getErrorCode());
            })
            .verifyComplete();

        verify(persistencePort).finalizeProcessingAtomically(
            argThat(d -> ProcessingResultCodes.PENDING.name().equals(d.getState()) && d.getRetryCount() == 1),
            argThat(h -> h.getErrorCode().equals(ProcessingResultCodes.GATEWAY_TIMEOUT.name()))
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
            .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any()))
            .thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.error(new ProcessingException(ProcessingResultCodes.GATEWAY_TIMEOUT.name(), "Final timeout", (Throwable) null)));

        StepVerifier.create(useCase.executePendingDocuments()
                .contextWrite(ctx -> ctx.put("message-id", "test-trace-max-retries")))
            .expectNextCount(1)
            .verifyComplete();

        verify(persistencePort).finalizeProcessingAtomically(
            argThat(d -> ProcessingResultCodes.FAILED.name().equals(d.getState())),
            any()
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
            .build();

        ProductDocumentFile file = ProductDocumentFile.builder()
                .productId("prod-1")
                .documentId("doc-1")
                .filename("test.pdf")
                .content(new byte[]{1, 2, 3})
                .size(3L)
                .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any()))
            .thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.just(file));
        
        when(documentValidator.validate(any(DocumentHistory.class), anyBoolean()))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.executePendingDocuments()
                .contextWrite(ctx -> ctx.put("message-id", "test-trace-success")))
            .expectNextCount(1)
            .verifyComplete();

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<DocumentHistoryDTO> historyCaptor = ArgumentCaptor.forClass(DocumentHistoryDTO.class);
        
        verify(persistencePort).finalizeProcessingAtomically(docCaptor.capture(), historyCaptor.capture());
        
        assertEquals(ProcessingResultCodes.PROCESSED.name(), docCaptor.getValue().getState());
        assertNotNull(historyCaptor.getValue().getCompletedAt());
    }
}
