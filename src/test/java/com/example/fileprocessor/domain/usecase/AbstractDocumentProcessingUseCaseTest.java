package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentFile;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
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

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbstractDocumentProcessingUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentHistoryRepository historyRepository;

    @Mock
    private ProductRestGateway productRestGateway;

    @Mock
    private RulesBussinesGateway documentValidator;

    private TestProcessingUseCase useCase;

    // Minimal concrete subclass for testing the abstract class
    static class TestProcessingUseCase extends AbstractDocumentProcessingUseCase {
        private final FileUploadResult result;

        TestProcessingUseCase(DocumentRepository documentRepository,
                             DocumentHistoryRepository historyRepository,
                             ProductRestGateway productRestGateway,
                             RulesBussinesGateway documentValidator,
                             FileUploadResult result) {
            super(documentRepository, historyRepository, productRestGateway, documentValidator);
            this.result = result;
        }

        @Override
        protected Mono<FileUploadResult> uploadDocument(ProductDocumentHistory doc, String productId, Long docId) {
            return Mono.just(result);
        }

        @Override
        protected String implementationName() {
            return "TEST";
        }
    }

    @BeforeEach
    void setUp() {
        useCase = new TestProcessingUseCase(documentRepository, historyRepository,
            productRestGateway, documentValidator,
            FileUploadResult.builder()
                .status(DocumentStatus.SUCCESS.name())
                .success(true)
                .build());
        lenient().when(historyRepository.save(any())).thenReturn(Mono.empty());
        lenient().when(documentRepository.updateStateById(anyLong(), anyString(), any())).thenReturn(Mono.empty());
        lenient().when(documentRepository.updateStateById(anyLong(), anyString(), anyString(), any())).thenReturn(Mono.just(1L));
    }

    @Test
    void traceFailure_whenFindLastAuditEmpty_usesRetry1() {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-1")
            .productId("prod-1")
            .name("test.pdf")
            .state(ProductState.PENDING)
            .useCase("TEST")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(documentRepository.findByStateAndUseCase(ProductState.PENDING, "TEST"))
            .thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.error(new RuntimeException("Force pipeline failure")));
        when(historyRepository.findLastAudit(eq(1L), eq("TEST")))
            .thenReturn(Mono.empty());

        StepVerifier.create(useCase.executePendingDocuments())
            .verifyComplete();

        ArgumentCaptor<DocumentHistory> captor = ArgumentCaptor.forClass(DocumentHistory.class);
        verify(historyRepository, atLeastOnce()).save(captor.capture());
        DocumentHistory saved = captor.getValue();
        assertEquals(1, saved.retry());
        assertEquals("FAILURE", saved.result());
    }

    @Test
    void traceFailure_whenLastAuditHasNullRetry_defaultsTo1() {
        Document doc = Document.builder()
            .id(2L)
            .documentId("doc-2")
            .productId("prod-2")
            .name("test2.pdf")
            .state(ProductState.PENDING)
            .useCase("TEST")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        DocumentHistory lastAudit = DocumentHistory.builder()
            .documentId(2L)
            .retry(null)
            .build();

        when(documentRepository.findByStateAndUseCase(ProductState.PENDING, "TEST"))
            .thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.error(new RuntimeException("Force pipeline failure")));
        when(historyRepository.findLastAudit(eq(2L), eq("TEST")))
            .thenReturn(Mono.just(lastAudit));

        StepVerifier.create(useCase.executePendingDocuments())
            .verifyComplete();

        ArgumentCaptor<DocumentHistory> captor = ArgumentCaptor.forClass(DocumentHistory.class);
        verify(historyRepository, atLeastOnce()).save(captor.capture());
        DocumentHistory saved = captor.getValue();
        assertEquals(1, saved.retry());
    }
}
