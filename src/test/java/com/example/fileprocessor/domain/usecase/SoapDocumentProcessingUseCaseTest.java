package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.Product;
import com.example.fileprocessor.domain.entity.ProductDocument;
import com.example.fileprocessor.domain.port.out.ProductDbGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.service.RulesBussinesService;
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SoapDocumentProcessingUseCaseTest {

    @Mock
    private ProductDbGateway productDbGateway;

    @Mock
    private ProductRestGateway productRestGateway;

    @Mock
    private SoapGateway soapGateway;

    @Mock
    private com.example.fileprocessor.domain.port.out.DocumentTraceabilityGateway traceabilityGateway;

    private SoapDocumentProcessingUseCase useCase;

    private static ProcessorsProperties.ProcessorConfig config(Long maxFileSizeBytes, String filenamePattern) {
        return new ProcessorsProperties.ProcessorConfig(maxFileSizeBytes, filenamePattern);
    }

    @BeforeEach
    void setUp() {
        var validator = new RulesBussinesService(config(null, null));
        useCase = new SoapDocumentProcessingUseCase(productDbGateway, productRestGateway, soapGateway, traceabilityGateway, validator);
        lenient().when(traceabilityGateway.save(any())).thenReturn(Mono.empty());
    }

    @Test
    void implementationName_returnsSOAP() {
        assertEquals("SOAP", useCase.implementationName());
    }

    @Test
    void uploadDocument_whenSuccess_returnsSuccessResult() {
        ProductDocument doc = new ProductDocument(
            "doc-1", "test.pdf", new byte[]{1}, "application/pdf", 1, false, "origin");

        FileUploadResult successResult = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .success(true)
            .correlationId("corr-456")
            .processedAt(Instant.now())
            .build();

        when(soapGateway.send(any(FileUploadRequest.class)))
            .thenReturn(Mono.just(successResult));

        StepVerifier.create(useCase.uploadDocument(doc, "prod-1"))
            .assertNext(result -> {
                assertTrue(result.isSuccess());
                assertEquals("corr-456", result.getCorrelationId());
            })
            .verifyComplete();
    }

    @Test
    void uploadDocument_whenError_returnsFailureResult() {
        ProductDocument doc = new ProductDocument(
            "doc-1", "test.pdf", new byte[]{1}, "application/pdf", 1, false, "origin");

        when(soapGateway.send(any(FileUploadRequest.class)))
            .thenReturn(Mono.error(new RuntimeException("SOAP error")));

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
        Product product = new Product("prod-1", "Test", LocalDateTime.now(), "ACTIVE", null, List.of(doc));

        FileUploadResult successResult = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .success(true)
            .build();

        when(productDbGateway.findByLoadDate(any())).thenReturn(Flux.just(product));
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(doc));
        when(soapGateway.send(any(FileUploadRequest.class))).thenReturn(Mono.just(successResult));

        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextCount(1)
            .verifyComplete();
    }

    @Test
    void executePendingDocuments_whenValidationFails_skipsDocument() {
        var validatorWithPattern = new RulesBussinesService(config(null, ".*\\.csv$"));
        useCase = new SoapDocumentProcessingUseCase(productDbGateway, productRestGateway, soapGateway, traceabilityGateway, validatorWithPattern);

        ProductDocument doc = new ProductDocument(
            "doc-1", "test.pdf", new byte[]{1}, "application/pdf", 1, false, "origin");
        Product product = new Product("prod-1", "Test", LocalDateTime.now(), "ACTIVE", null, List.of(doc));

        when(productDbGateway.findByLoadDate(any())).thenReturn(Flux.just(product));
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(doc));

        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextCount(0)
            .verifyComplete();

        verify(soapGateway, never()).send(any(FileUploadRequest.class));
    }

    @Test
    void executePendingDocuments_whenSizeExceedsLimit_skipsDocument() {
        var validatorWithSize = new RulesBussinesService(config(100L, null));
        useCase = new SoapDocumentProcessingUseCase(productDbGateway, productRestGateway, soapGateway, traceabilityGateway, validatorWithSize);

        ProductDocument doc = new ProductDocument(
            "doc-1", "test.pdf", new byte[]{1}, "application/pdf", 500, false, "origin");
        Product product = new Product("prod-1", "Test", LocalDateTime.now(), "ACTIVE", null, List.of(doc));

        when(productDbGateway.findByLoadDate(any())).thenReturn(Flux.just(product));
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(doc));

        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextCount(0)
            .verifyComplete();

        verify(soapGateway, never()).send(any(FileUploadRequest.class));
    }
}
