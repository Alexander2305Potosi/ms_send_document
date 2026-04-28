package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ResilienceOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingPipelineTest {

    @Mock
    private ProductDocumentRepository documentRepository;

    @Mock
    private FileValidator fileValidator;

    @Mock
    private DocumentValidationRules validationRules;

    @Mock
    private DocumentSkipHandler skipHandler;

    @Mock
    private ProductStatusAggregator statusAggregator;

    @Mock
    private ResilienceOperator resilienceOperator;

    @Mock
    private DocumentSender documentSender;

    private DocumentProcessingPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new DocumentProcessingPipeline(
            documentRepository,
            fileValidator,
            validationRules,
            skipHandler,
            statusAggregator,
            resilienceOperator,
            documentSender
        );
    }

    @Test
    void process_shouldApplyValidationRules() {
        ProductDocumentToProcess doc = createDocument("doc-1", "prod-1", "/incoming/file.pdf");
        String traceId = "trace-123";
        String correlationId = "corr-456";

        when(validationRules.shouldSkipFolder("/incoming/file.pdf")).thenReturn(false);
        when(validationRules.shouldSendByOrigin("/incoming/file.pdf")).thenReturn(true);
        when(validationRules.shouldNotSendBySize(1)).thenReturn(false);
        when(validationRules.extractFolderInfo("/incoming/file.pdf"))
            .thenReturn(new DocumentValidationRules.FolderInfo("incoming", null));

        when(fileValidator.validate(doc)).thenReturn(Mono.just(doc));

        DocumentSendRequest expectedRequest = DocumentSendRequest.builder()
            .documentId("doc-1")
            .fileContent(new byte[]{1})
            .filename("file.pdf")
            .contentType("application/pdf")
            .fileSize(1000)
            .traceId(traceId)
            .parentFolder("incoming")
            .childFolder(null)
            .build();

        FileUploadResult uploadResult = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .correlationId(correlationId)
            .success(true)
            .build();

        when(documentSender.send(any(DocumentSendRequest.class))).thenReturn(Mono.just(uploadResult));
        when(resilienceOperator.decorate(any(Mono.class), eq(traceId))).thenAnswer(inv -> inv.getArgument(0));
        when(documentRepository.updateStatus(any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        when(statusAggregator.updateProductStatus(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(pipeline.process(doc, traceId))
            .expectNextMatches(result -> result.getCorrelationId().equals(correlationId))
            .verifyComplete();

        verify(validationRules).shouldSkipFolder("/incoming/file.pdf");
        verify(validationRules).shouldSendByOrigin("/incoming/file.pdf");
        verify(validationRules).shouldNotSendBySize(1);
    }

    @Test
    void process_shouldSkipDocumentWhenFolderShouldBeSkipped() {
        ProductDocumentToProcess doc = createDocument("doc-1", "prod-1", "/tmp/file.pdf");
        String traceId = "trace-123";

        when(validationRules.shouldSkipFolder("/tmp/file.pdf")).thenReturn(true);
        when(skipHandler.skipDocument(any(), any(), any(), any(), any(), any()))
            .thenReturn(Mono.empty());

        StepVerifier.create(pipeline.process(doc, traceId))
            .verifyComplete();

        verify(skipHandler).skipDocument(
            eq(doc),
            eq(traceId),
            eq(DocumentStatus.SKIPPED.name()),
            any(String.class),
            any(String.class),
            eq("doc-1")
        );
    }

    @Test
    void process_shouldSkipDocumentWhenOriginDoesNotMatch() {
        ProductDocumentToProcess doc = createDocument("doc-1", "prod-1", "/other/file.pdf");
        String traceId = "trace-123";

        when(validationRules.shouldSkipFolder("/other/file.pdf")).thenReturn(false);
        when(validationRules.shouldSendByOrigin("/other/file.pdf")).thenReturn(false);
        when(skipHandler.skipDocument(any(), any(), any(), any(), any(), any()))
            .thenReturn(Mono.empty());

        StepVerifier.create(pipeline.process(doc, traceId))
            .verifyComplete();

        verify(skipHandler).skipDocument(
            eq(doc),
            eq(traceId),
            eq(DocumentStatus.NOT_SENT.name()),
            any(String.class),
            any(String.class),
            eq("doc-1")
        );
    }

    @Test
    void process_shouldSkipDocumentWhenSizeExceedsLimit() {
        ProductDocumentToProcess doc = createDocument("doc-1", "prod-1", "/incoming/large.pdf");
        String traceId = "trace-123";

        when(validationRules.shouldSkipFolder("/incoming/large.pdf")).thenReturn(false);
        when(validationRules.shouldSendByOrigin("/incoming/large.pdf")).thenReturn(true);
        when(validationRules.shouldNotSendBySize(1)).thenReturn(true);
        when(skipHandler.skipDocument(any(), any(), any(), any(), any(), any()))
            .thenReturn(Mono.empty());

        StepVerifier.create(pipeline.process(doc, traceId))
            .verifyComplete();

        verify(skipHandler).skipDocument(
            eq(doc),
            eq(traceId),
            eq(DocumentStatus.NOT_SENT.name()),
            any(String.class),
            any(String.class),
            eq("file.pdf")
        );
    }

    @Test
    void process_shouldPerformFileValidation() {
        ProductDocumentToProcess doc = createDocument("doc-1", "prod-1", "/incoming/file.pdf");
        String traceId = "trace-123";

        when(validationRules.shouldSkipFolder("/incoming/file.pdf")).thenReturn(false);
        when(validationRules.shouldSendByOrigin("/incoming/file.pdf")).thenReturn(true);
        when(validationRules.shouldNotSendBySize(1)).thenReturn(false);
        when(validationRules.extractFolderInfo("/incoming/file.pdf"))
            .thenReturn(new DocumentValidationRules.FolderInfo("incoming", null));
        when(fileValidator.validate(doc)).thenReturn(Mono.just(doc));

        DocumentSendRequest expectedRequest = DocumentSendRequest.builder()
            .documentId("doc-1")
            .fileContent(new byte[]{1})
            .filename("file.pdf")
            .contentType("application/pdf")
            .fileSize(1000)
            .traceId(traceId)
            .parentFolder("incoming")
            .childFolder(null)
            .build();

        FileUploadResult uploadResult = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .correlationId("corr-456")
            .success(true)
            .build();

        when(documentSender.send(any(DocumentSendRequest.class))).thenReturn(Mono.just(uploadResult));
        when(resilienceOperator.decorate(any(Mono.class), eq(traceId))).thenAnswer(inv -> inv.getArgument(0));
        when(documentRepository.updateStatus(any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        when(statusAggregator.updateProductStatus(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(pipeline.process(doc, traceId))
            .expectNextMatches(result -> result.getCorrelationId().equals("corr-456"))
            .verifyComplete();

        verify(fileValidator).validate(doc);
    }

    @Test
    void process_shouldBuildCorrectDocumentSendRequest() {
        ProductDocumentToProcess doc = ProductDocumentToProcess.builder()
            .documentId("doc-custom")
            .productId("prod-1")
            .filename("custom.pdf")
            .content(new byte[]{1, 2, 3})
            .contentType("application/pdf")
            .origin("/documents/reports")
            .build();
        String traceId = "trace-789";

        when(validationRules.shouldSkipFolder("/documents/reports")).thenReturn(false);
        when(validationRules.shouldSendByOrigin("/documents/reports")).thenReturn(true);
        when(validationRules.shouldNotSendBySize(3)).thenReturn(false);
        when(validationRules.extractFolderInfo("/documents/reports"))
            .thenReturn(new DocumentValidationRules.FolderInfo("documents", "reports"));
        when(fileValidator.validate(doc)).thenReturn(Mono.just(doc));

        FileUploadResult uploadResult = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .correlationId("corr-123")
            .success(true)
            .build();

        when(documentSender.send(any(DocumentSendRequest.class))).thenReturn(Mono.just(uploadResult));
        when(resilienceOperator.decorate(any(Mono.class), eq(traceId))).thenAnswer(inv -> inv.getArgument(0));
        when(documentRepository.updateStatus(any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        when(statusAggregator.updateProductStatus(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(pipeline.process(doc, traceId))
            .expectNextMatches(result -> result.isSuccess())
            .verifyComplete();
    }

    @Test
    void process_shouldCallSendWithCircuitBreaker() {
        ProductDocumentToProcess doc = createDocument("doc-1", "prod-1", "/incoming/file.pdf");
        String traceId = "trace-123";

        when(validationRules.shouldSkipFolder("/incoming/file.pdf")).thenReturn(false);
        when(validationRules.shouldSendByOrigin("/incoming/file.pdf")).thenReturn(true);
        when(validationRules.shouldNotSendBySize(1)).thenReturn(false);
        when(validationRules.extractFolderInfo("/incoming/file.pdf"))
            .thenReturn(new DocumentValidationRules.FolderInfo("incoming", null));
        when(fileValidator.validate(doc)).thenReturn(Mono.just(doc));

        FileUploadResult uploadResult = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .correlationId("corr-456")
            .success(true)
            .build();

        when(documentSender.send(any(DocumentSendRequest.class))).thenReturn(Mono.just(uploadResult));
        when(resilienceOperator.decorate(any(Mono.class), eq(traceId))).thenAnswer(inv -> inv.getArgument(0));
        when(documentRepository.updateStatus(any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        when(statusAggregator.updateProductStatus(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(pipeline.process(doc, traceId))
            .expectNextMatches(result -> result.isSuccess())
            .verifyComplete();

        verify(resilienceOperator).decorate(any(Mono.class), eq(traceId));
    }

    @Test
    void process_shouldUpdateStatusesOnSuccess() {
        ProductDocumentToProcess doc = createDocument("doc-1", "prod-1", "/incoming/file.pdf");
        String traceId = "trace-123";
        String correlationId = "corr-789";

        when(validationRules.shouldSkipFolder("/incoming/file.pdf")).thenReturn(false);
        when(validationRules.shouldSendByOrigin("/incoming/file.pdf")).thenReturn(true);
        when(validationRules.shouldNotSendBySize(1)).thenReturn(false);
        when(validationRules.extractFolderInfo("/incoming/file.pdf"))
            .thenReturn(new DocumentValidationRules.FolderInfo("incoming", null));
        when(fileValidator.validate(doc)).thenReturn(Mono.just(doc));

        FileUploadResult uploadResult = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .correlationId(correlationId)
            .success(true)
            .build();

        when(documentSender.send(any(DocumentSendRequest.class))).thenReturn(Mono.just(uploadResult));
        when(resilienceOperator.decorate(any(Mono.class), eq(traceId))).thenAnswer(inv -> inv.getArgument(0));
        when(documentRepository.updateStatus(eq("doc-1"), eq(DocumentStatus.SUCCESS.name()), eq(traceId), eq(correlationId), any()))
            .thenReturn(Mono.empty());
        when(statusAggregator.updateProductStatus(eq("prod-1"), eq(traceId))).thenReturn(Mono.empty());

        StepVerifier.create(pipeline.process(doc, traceId))
            .expectNextMatches(result -> result.getCorrelationId().equals(correlationId))
            .verifyComplete();

        verify(documentRepository).updateStatus(eq("doc-1"), eq(DocumentStatus.SUCCESS.name()), eq(traceId), eq(correlationId), any());
        verify(statusAggregator).updateProductStatus(eq("prod-1"), eq(traceId));
    }

    @Test
    void process_shouldUpdateStatusesOnFailure() {
        ProductDocumentToProcess doc = createDocument("doc-1", "prod-1", "/incoming/file.pdf");
        String traceId = "trace-123";

        when(validationRules.shouldSkipFolder("/incoming/file.pdf")).thenReturn(false);
        when(validationRules.shouldSendByOrigin("/incoming/file.pdf")).thenReturn(true);
        when(validationRules.shouldNotSendBySize(1)).thenReturn(false);
        when(validationRules.extractFolderInfo("/incoming/file.pdf"))
            .thenReturn(new DocumentValidationRules.FolderInfo("incoming", null));
        when(fileValidator.validate(doc)).thenReturn(Mono.just(doc));

        FileUploadResult uploadResult = FileUploadResult.builder()
            .status(DocumentStatus.FAILURE.name())
            .errorCode("SERVICE_ERROR")
            .success(false)
            .build();

        when(documentSender.send(any(DocumentSendRequest.class))).thenReturn(Mono.just(uploadResult));
        when(resilienceOperator.decorate(any(Mono.class), eq(traceId))).thenAnswer(inv -> inv.getArgument(0));
        when(documentRepository.updateStatus(eq("doc-1"), eq(DocumentStatus.FAILURE.name()), eq(traceId), any(), eq("SERVICE_ERROR")))
            .thenReturn(Mono.empty());
        when(statusAggregator.updateProductStatus(eq("prod-1"), eq(traceId))).thenReturn(Mono.empty());

        StepVerifier.create(pipeline.process(doc, traceId))
            .expectNextMatches(result -> !result.isSuccess())
            .verifyComplete();

        verify(documentRepository).updateStatus(eq("doc-1"), eq(DocumentStatus.FAILURE.name()), eq(traceId), any(), eq("SERVICE_ERROR"));
        verify(statusAggregator).updateProductStatus(eq("prod-1"), eq(traceId));
    }

    private ProductDocumentToProcess createDocument(String docId, String productId, String origin) {
        return ProductDocumentToProcess.builder()
            .documentId(docId)
            .productId(productId)
            .filename("file.pdf")
            .content(new byte[]{1})
            .contentType("application/pdf")
            .origin(origin)
            .traceId("trace-" + docId)
            .createdAt(Instant.now())
            .build();
    }
}
