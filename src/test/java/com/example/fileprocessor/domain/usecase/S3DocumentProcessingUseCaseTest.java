package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3DocumentProcessingUseCaseTest {

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
            historyRepository,
            productRestGateway,
            s3Gateway,
            documentValidator
        );
        lenient().when(historyRepository.save(any())).thenReturn(Mono.empty());
        lenient().when(historyRepository.updateStateAndUseCase(anyString(), anyString(), anyString())).thenReturn(Mono.empty());
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

        StepVerifier.create(useCase.uploadDocument(doc(), "prod-1"))
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

        StepVerifier.create(useCase.uploadDocument(doc(), "prod-1"))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(DocumentStatus.FAILURE.name(), result.getStatus());
            })
            .verifyComplete();
    }
}
