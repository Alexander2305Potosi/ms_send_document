package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductHistory;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductDocumentFile;
import com.example.fileprocessor.domain.port.out.CategoryManualRepository;
import com.example.fileprocessor.domain.port.out.PaisHomologadoRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.service.RulesBussinesService;
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.CategoryManualEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.PaisHomologadoEntity;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SoapDocumentProcessingUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductRestGateway productRestGateway;

    @Mock
    private SoapGateway soapGateway;

    @Mock
    private com.example.fileprocessor.domain.port.out.DocumentHistoryRepository historyRepository;

    @Mock
    private CategoryManualRepository categoryRepository;

    @Mock
    private PaisHomologadoRepository paisRepository;

    private SoapDocumentProcessingUseCase useCase;

    private static ProcessorsProperties.ProcessorConfig config(Long maxFileSizeBytes, String filenamePattern) {
        return new ProcessorsProperties.ProcessorConfig(maxFileSizeBytes, filenamePattern);
    }

    @BeforeEach
    void setUp() {
        var validator = new RulesBussinesService(config(null, null));
        useCase = new SoapDocumentProcessingUseCase(productRepository, productRestGateway, soapGateway, historyRepository, validator, categoryRepository, paisRepository);
        lenient().when(historyRepository.save(any())).thenReturn(Mono.empty());
        lenient().when(productRepository.updateEstado(anyString(), any())).thenReturn(Mono.empty());
        lenient().when(productRepository.updateEstadoById(anyLong(), anyString())).thenReturn(Mono.empty());
    }

    @Test
    void implementationName_returnsSOAP() {
        assertEquals("SOAP", useCase.implementationName());
    }

    @Test
    void uploadDocument_whenSuccess_returnsSuccessResult() {
        ProductDocumentHistory doc = new ProductDocumentHistory(
            "doc-1", "test.pdf", new byte[]{1}, "application/pdf", 1L, false, "origin", "AR");

        CategoryManualEntity categoryEntity = CategoryManualEntity.builder()
            .categoria("origin")
            .descripcionManual("Manual de Origin")
            .build();
        PaisHomologadoEntity paisEntity = PaisHomologadoEntity.builder()
            .pais("AR")
            .paisHomologado("Argentina")
            .build();

        when(categoryRepository.findByCategoria("origin")).thenReturn(Mono.just(categoryEntity));
        when(paisRepository.findByPais("AR")).thenReturn(Mono.just(paisEntity));

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
        ProductDocumentHistory doc = new ProductDocumentHistory(
            "doc-1", "test.pdf", new byte[]{1}, "application/pdf", 1L, false, "origin", "AR");

        CategoryManualEntity categoryEntity = CategoryManualEntity.builder()
            .categoria("origin")
            .descripcionManual("Manual de Origin")
            .build();
        PaisHomologadoEntity paisEntity = PaisHomologadoEntity.builder()
            .pais("AR")
            .paisHomologado("Argentina")
            .build();

        when(categoryRepository.findByCategoria("origin")).thenReturn(Mono.just(categoryEntity));
        when(paisRepository.findByPais("AR")).thenReturn(Mono.just(paisEntity));
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
        ProductDocumentHistory doc = new ProductDocumentHistory(
            "doc-1", "test.pdf", new byte[]{1}, "application/pdf", 1L, false, "origin", "AR");
        ProductDocumentFile docFile = ProductDocumentFile.builder()
            .documentId(doc.documentId())
            .filename(doc.filename())
            .content(doc.content())
            .contentType(doc.contentType())
            .size(doc.size())
            .isZip(doc.isZip())
            .origin(doc.origin())
            .pais(doc.pais())
            .build();
        ProductHistory product = new ProductHistory(1L, "prod-1", "Test", LocalDateTime.now(), "ACTIVE", null, List.of(doc));

        CategoryManualEntity categoryEntity = CategoryManualEntity.builder()
            .categoria("origin")
            .descripcionManual("Manual de Origin")
            .build();
        PaisHomologadoEntity paisEntity = PaisHomologadoEntity.builder()
            .pais("AR")
            .paisHomologado("Argentina")
            .build();

        FileUploadResult successResult = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .success(true)
            .build();

        when(productRepository.findByLoadDate(any())).thenReturn(Flux.just(product));
        when(productRepository.updateEstadoById(anyLong(), anyString())).thenReturn(Mono.empty());
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(docFile));
        when(categoryRepository.findByCategoria("origin")).thenReturn(Mono.just(categoryEntity));
        when(paisRepository.findByPais("AR")).thenReturn(Mono.just(paisEntity));
        when(soapGateway.send(any(FileUploadRequest.class))).thenReturn(Mono.just(successResult));

        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextCount(1)
            .verifyComplete();
    }

    @Test
    void executePendingDocuments_whenValidationFails_emitsFailureResult() {
        var validatorWithPattern = new RulesBussinesService(config(null, ".*\\.csv$"));
        useCase = new SoapDocumentProcessingUseCase(productRepository, productRestGateway, soapGateway, historyRepository, validatorWithPattern, categoryRepository, paisRepository);

        ProductDocumentHistory doc = new ProductDocumentHistory(
            "doc-1", "test.pdf", new byte[]{1}, "application/pdf", 1L, false, "origin", "AR");
        ProductDocumentFile docFile = ProductDocumentFile.builder()
            .documentId(doc.documentId())
            .filename(doc.filename())
            .content(doc.content())
            .contentType(doc.contentType())
            .size(doc.size())
            .isZip(doc.isZip())
            .origin(doc.origin())
            .pais(doc.pais())
            .build();
        ProductHistory product = new ProductHistory(1L, "prod-1", "Test", LocalDateTime.now(), "ACTIVE", null, List.of(doc));

        when(productRepository.findByLoadDate(any())).thenReturn(Flux.just(product));
        when(productRepository.updateEstadoById(anyLong(), anyString())).thenReturn(Mono.empty());
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(docFile));

        StepVerifier.create(useCase.executePendingDocuments())
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(DocumentStatus.FAILURE.name(), result.getStatus());
            })
            .verifyComplete();

        verify(historyRepository, times(1)).save(any());
        verify(soapGateway, never()).send(any(FileUploadRequest.class));
    }

    @Test
    void executePendingDocuments_whenSizeExceedsLimit_emitsFailureResult() {
        var validatorWithSize = new RulesBussinesService(config(100L, null));
        useCase = new SoapDocumentProcessingUseCase(productRepository, productRestGateway, soapGateway, historyRepository, validatorWithSize, categoryRepository, paisRepository);

        ProductDocumentHistory doc = new ProductDocumentHistory(
            "doc-1", "test.pdf", new byte[]{1}, "application/pdf", 500L, false, "origin", "AR");
        ProductDocumentFile docFile = ProductDocumentFile.builder()
            .documentId(doc.documentId())
            .filename(doc.filename())
            .content(doc.content())
            .contentType(doc.contentType())
            .size(doc.size())
            .isZip(doc.isZip())
            .origin(doc.origin())
            .pais(doc.pais())
            .build();
        ProductHistory product = new ProductHistory(1L, "prod-1", "Test", LocalDateTime.now(), "ACTIVE", null, List.of(doc));

        when(productRepository.findByLoadDate(any())).thenReturn(Flux.just(product));
        when(productRepository.updateEstadoById(anyLong(), anyString())).thenReturn(Mono.empty());
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(docFile));

        StepVerifier.create(useCase.executePendingDocuments())
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(DocumentStatus.FAILURE.name(), result.getStatus());
            })
            .verifyComplete();

        verify(historyRepository, times(1)).save(any());
        verify(soapGateway, never()).send(any(FileUploadRequest.class));
    }
}