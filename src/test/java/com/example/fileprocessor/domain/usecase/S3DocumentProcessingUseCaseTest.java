package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
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
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.infrastructure.drivenadapters.aws.S3ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3DocumentProcessingUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentHistoryRepository historyRepository;

    @Mock
    private ProductRestGateway productRestGateway;

    @Mock
    private S3Gateway s3Gateway;

    @Mock
    private RulesBussinesGateway documentValidator;

    private S3DocumentProcessingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new S3DocumentProcessingUseCase(
            documentRepository,
            historyRepository,
            productRestGateway,
            s3Gateway,
            documentValidator
        );
        lenient().when(historyRepository.save(any())).thenReturn(Mono.empty());
        lenient().when(historyRepository.findLastAudit(anyLong(), anyString())).thenReturn(Mono.empty());
        lenient().when(documentRepository.updateStateById(anyLong(), anyString(), any())).thenReturn(Mono.empty());
        lenient().when(documentRepository.updateStateById(anyLong(), anyString(), anyString(), any())).thenReturn(Mono.just(1L));
    }

    private static ProductDocumentHistory doc() {
        return ProductDocumentHistory.builder()
            .productId("prod-1")
            .isZip(false)
            .pais("AR")
            .documentId("doc-1")
            .name("test.pdf")
            .filename("test.pdf")
            .contentType("application/pdf")
            .size(1L)
            .origin("origin")
            .content(new byte[]{1})
            .build();
    }

    @Test
    void implementationName_returnsS3() {
        assertEquals("S3", useCase.implementationName());
    }

    @Test
    void uploadDocument_whenSuccess_returnsSuccessResult() {
        FileUploadResult successResult = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .success(true)
            .correlationId("corr-123")
            .processedAt(Instant.now())
            .build();

        when(s3Gateway.send(any(FileUploadRequest.class)))
            .thenReturn(Mono.just(successResult));

        StepVerifier.create(useCase.uploadDocument(doc(), "prod-1", 1L))
            .assertNext(result -> {
                assertTrue(result.isSuccess());
                assertEquals("corr-123", result.getCorrelationId());
            })
            .verifyComplete();
    }

    @Test
    void uploadDocument_whenError_returnsFailureResult() {
        when(s3Gateway.send(any(FileUploadRequest.class)))
            .thenReturn(Mono.error(new RuntimeException("S3 error")));

        StepVerifier.create(useCase.uploadDocument(doc(), "prod-1", 1L))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(DocumentStatus.FAILURE.name(), result.getStatus());
            })
            .verifyComplete();
    }

    @Test
    void uploadDocument_whenGatewayReturnsFailureStatus_propagatesFailure() {
        FileUploadResult failureResult = FileUploadResult.builder()
            .status(DocumentStatus.FAILURE.name())
            .success(false)
            .errorCode(S3ErrorCodes.SERVICE_UNAVAILABLE)
            .message("S3 unavailable")
            .build();

        when(s3Gateway.send(any(FileUploadRequest.class)))
            .thenReturn(Mono.just(failureResult));

        StepVerifier.create(useCase.uploadDocument(doc(), "prod-1", 1L))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(S3ErrorCodes.SERVICE_UNAVAILABLE, result.getErrorCode());
            })
            .verifyComplete();
    }

    @Test
    void executePendingDocuments_singleDocument_fullPipelineSuccess() {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-1")
            .productId("prod-1")
            .name("test.pdf")
            .state(ProductState.PENDING)
            .useCase("S3")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        ProductDocumentFile file = ProductDocumentFile.builder()
            .documentId("doc-1")
            .filename("test.pdf")
            .content(new byte[]{1, 2, 3})
            .contentType("application/pdf")
            .size(3L)
            .isZip(false)
            .origin("origin")
            .pais("AR")
            .build();

        FileUploadResult successResult = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .success(true)
            .correlationId("corr-123")
            .processedAt(Instant.now())
            .build();

        when(documentRepository.findByStateAndUseCase(ProductState.PENDING, "S3"))
            .thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument("prod-1", "doc-1"))
            .thenReturn(Mono.just(file));
        when(documentValidator.validate(any(), eq(true)))
            .thenReturn(Mono.just(doc()));
        when(s3Gateway.send(any(FileUploadRequest.class)))
            .thenReturn(Mono.just(successResult));

        StepVerifier.create(useCase.executePendingDocuments())
            .assertNext(result -> {
                assertTrue(result.isSuccess());
                assertEquals("corr-123", result.getCorrelationId());
            })
            .verifyComplete();
    }
}
