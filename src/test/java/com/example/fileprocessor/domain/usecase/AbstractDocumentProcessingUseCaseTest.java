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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                return Mono.empty();
            }

            @Override
            protected String implementationName() {
                return "TEST";
            }
        };

        lenient().when(persistencePort.resetStaleDocumentsToday(anyString(), any(), any()))
            .thenReturn(Mono.just(0L));

        lenient().when(persistencePort.lockDocumentForProcessing(anyLong(), anyInt()))
            .thenReturn(Mono.just(1L));

        lenient().when(persistencePort.finalizeProcessingAtomically(any()))
            .thenAnswer(invocation -> {
                com.example.fileprocessor.domain.entity.FinalizeProcessingCommand cmd = invocation.getArgument(0);
                return Mono.just(cmd.response());
            });
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
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.error(new ProcessingException("GATEWAY_TIMEOUT", "Timeout error", (Throwable) null)));

        StepVerifier.create(useCase.executePendingDocuments())
            .assertNext(result -> {
                assertEquals(ProcessingResultCodes.FAILURE.name(), result.getStatus());
                assertEquals("GATEWAY_TIMEOUT", result.getErrorCode());
            })
            .verifyComplete();

        verify(persistencePort).finalizeProcessingAtomically(argThat(cmd -> 
            cmd.finalState().equals(ProductState.PENDING) && 
            cmd.nextRetryCount() == 1
        ));
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

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any()))
            .thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.error(new ProcessingException("GATEWAY_TIMEOUT", "Final timeout", (Throwable) null)));

        StepVerifier.create(useCase.executePendingDocuments())
            .assertNext(result -> {
                assertEquals(ProcessingResultCodes.FAILURE.name(), result.getStatus());
            })
            .verifyComplete();

        verify(persistencePort).finalizeProcessingAtomically(argThat(cmd -> 
            cmd.finalState().equals(ProductState.FAILED) && 
            cmd.nextRetryCount() == 3
        ));
    }
}
