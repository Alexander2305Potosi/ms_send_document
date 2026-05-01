package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.Product;
import com.example.fileprocessor.domain.entity.ProductDocument;
import com.example.fileprocessor.domain.port.out.DocumentValidationGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.service.DefaultDocumentValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3DocumentProcessingUseCaseTest {

    @Mock
    private ProductRestGateway productRestGateway;

    @Mock
    private com.example.fileprocessor.domain.port.out.S3Gateway s3Gateway;

    private S3DocumentProcessingUseCase useCase;

    @BeforeEach
    void setUp() {
        DocumentValidationGateway validator = new DefaultDocumentValidationService(List.of());
        useCase = new S3DocumentProcessingUseCase(productRestGateway, s3Gateway, validator);
    }

    @Test
    void implementationName_returnsS3() {
        assertEquals("S3", useCase.implementationName());
    }

    @Test
    void uploadDocument_whenSuccess_returnsSuccessResult() {
        ProductDocument doc = new ProductDocument(
            "doc-1", "test.pdf", new byte[]{1}, "application/pdf", 1, false, "origin");

        FileUploadResult successResult = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .success(true)
            .correlationId("corr-123")
            .processedAt(Instant.now())
            .build();

        when(s3Gateway.send(any(FileUploadRequest.class)))
            .thenReturn(Mono.just(successResult));

        StepVerifier.create(useCase.uploadDocument(doc, "prod-1"))
            .assertNext(result -> {
                assertTrue(result.isSuccess());
                assertEquals("corr-123", result.getCorrelationId());
            })
            .verifyComplete();
    }

    @Test
    void uploadDocument_whenError_returnsFailureResult() {
        ProductDocument doc = new ProductDocument(
            "doc-1", "test.pdf", new byte[]{1}, "application/pdf", 1, false, "origin");

        when(s3Gateway.send(any(FileUploadRequest.class)))
            .thenReturn(Mono.error(new RuntimeException("S3 error")));

        StepVerifier.create(useCase.uploadDocument(doc, "prod-1"))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(DocumentStatus.FAILURE.name(), result.getStatus());
            })
            .verifyComplete();
    }

    @Test
    void executePendingDocuments_processesAllDocuments() {
        ProductDocument doc = new ProductDocument(
            "doc-1", "test.pdf", new byte[]{1}, "application/pdf", 1, false, "origin");
        Product product = new Product("prod-1", "Test", List.of(doc));

        FileUploadResult successResult = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .success(true)
            .build();

        when(productRestGateway.getAllProducts()).thenReturn(Flux.just(product));
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(doc));
        when(s3Gateway.send(any(FileUploadRequest.class))).thenReturn(Mono.just(successResult));

        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextCount(1)
            .verifyComplete();
    }

    @Test
    void executePendingDocuments_whenError_logsAndContinues() {
        ProductDocument doc = new ProductDocument(
            "doc-1", "test.pdf", new byte[]{1}, "application/pdf", 1, false, "origin");
        Product product = new Product("prod-1", "Test", List.of(doc));

        FileUploadResult failureResult = FileUploadResult.builder()
            .status(DocumentStatus.FAILURE.name())
            .success(false)
            .build();

        when(productRestGateway.getAllProducts()).thenReturn(Flux.just(product));
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(doc));
        when(s3Gateway.send(any(FileUploadRequest.class))).thenReturn(Mono.just(failureResult));

        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextCount(1)
            .verifyComplete();
    }
}
