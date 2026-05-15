package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
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
            protected Mono<FileUploadResponse> uploadDocument(com.example.fileprocessor.domain.entity.ProductDocumentHistory doc, String productId, Long docId) {
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
            .state(ProductState.PENDING)
            .retryCount(0)
            .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any()))
            .thenReturn(Flux.just(doc));
        
        // Simular error de red (reintentable según ProcessingResultCodes.isTransient)
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.error(new ProcessingException(ProcessingResultCodes.GATEWAY_TIMEOUT.name(), "Timeout error", (Throwable) null)));

        StepVerifier.create(useCase.executePendingDocuments()
                .contextWrite(ctx -> ctx.put("message-id", "test-trace-tech-error")))
            .assertNext(result -> {
                assertEquals(ProcessingResultCodes.GATEWAY_TIMEOUT.name(), result.getErrorCode());
            })
            .verifyComplete();

        // Verificar que el documento vuelve a PENDING e incrementa el reintento
        verify(persistencePort).finalizeProcessingAtomically(
            argThat(d -> d.getState().equals(ProductState.PENDING) && d.getRetryCount() == 1),
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
            .state(ProductState.PENDING)
            .retryCount(3) // Máximo alcanzado
            .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any()))
            .thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.error(new ProcessingException(ProcessingResultCodes.GATEWAY_TIMEOUT.name(), "Final timeout", (Throwable) null)));

        StepVerifier.create(useCase.executePendingDocuments()
                .contextWrite(ctx -> ctx.put("message-id", "test-trace-max-retries")))
            .expectNextCount(1)
            .verifyComplete();

        // Verificar que pasa a FAILED
        verify(persistencePort).finalizeProcessingAtomically(
            argThat(d -> d.getState().equals(ProductState.FAILED)),
            any()
        );
    }

    @Test
    void executePendingDocuments_withBusinessRuleError_marksAsFailedWithoutRetry() {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-1")
            .productId("prod-1")
            .name("test.pdf")
            .state(ProductState.PENDING)
            .retryCount(0)
            .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any()))
            .thenReturn(Flux.just(doc));
        
        // Simular error de regla de negocio (no reintentable según ProcessingResultCodes.isBusinessRule)
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.error(new ProcessingException(ProcessingResultCodes.INVALID_BASE64.name(), "Formato inválido", (Throwable) null)));

        StepVerifier.create(useCase.executePendingDocuments()
                .contextWrite(ctx -> ctx.put("message-id", "test-trace-business-error")))
            .expectNextCount(1)
            .verifyComplete();

        // Verificar que pasa a FAILED directamente (retryCount sigue en 0)
        verify(persistencePort).finalizeProcessingAtomically(
            argThat(d -> d.getState().equals(ProductState.FAILED) && d.getRetryCount() == 0),
            argThat(h -> h.getErrorCode().equals(ProcessingResultCodes.INVALID_BASE64.name()))
        );
    }

    @Test
    void executePendingDocuments_withSuccess_marksAsProcessed() {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-1")
            .productId("prod-1")
            .name("test.pdf")
            .state(ProductState.PENDING)
            .retryCount(0)
            .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any()))
            .thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.just(com.example.fileprocessor.domain.entity.ProductDocumentFile.builder()
                .filename("test.pdf")
                .productId("prod-1")
                .documentId("doc-1")
                .build()));
        
        when(documentValidator.validate(any(), anyBoolean()))
            .thenReturn(Mono.just(com.example.fileprocessor.domain.entity.ProductDocumentHistory.builder()
                .productId("prod-1")
                .documentId("doc-1")
                .build()));

        StepVerifier.create(useCase.executePendingDocuments()
                .contextWrite(ctx -> ctx.put("message-id", "test-trace-success")))
            .expectNextCount(1)
            .verifyComplete();

        // Usar captor para validación precisa
        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<com.example.fileprocessor.domain.entity.DocumentHistoryDTO> historyCaptor = ArgumentCaptor.forClass(com.example.fileprocessor.domain.entity.DocumentHistoryDTO.class);
        
        verify(persistencePort).finalizeProcessingAtomically(docCaptor.capture(), historyCaptor.capture());
        
        assertEquals(ProductState.PROCESSED, docCaptor.getValue().getState(), "El estado del documento debería ser PROCESSED");
        assertNotNull(historyCaptor.getValue().getCompletedAt());
    }
}
