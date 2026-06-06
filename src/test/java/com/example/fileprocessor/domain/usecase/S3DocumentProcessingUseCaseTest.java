package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.product.maestro.ProductDocumentFile;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3DocumentProcessingUseCaseTest {

    @Mock
    private DocumentPersistenceGateway persistencePort;
    @Mock
    private ProductRestGateway productRestGateway;
    @Mock
    private S3Gateway s3Gateway;
    @Mock
    private RulesBussinesGateway documentValidator;

    private S3DocumentProcessingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new S3DocumentProcessingUseCase(persistencePort, productRestGateway, s3Gateway, documentValidator, "/tmp/test-zip-dir");
    }

    @Test
    void executePendingDocuments_withS3_success() {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-1")
            .productId("prod-1")
            .name("test.pdf")
            .retryCount(0)
            .isZip(false)
            .build();

        ProductDocumentFile file = ProductDocumentFile.builder()
            .productId("prod-1")
            .documentId("doc-1")
            .filename("test.pdf")
            .content(new byte[]{1})
            .isZip(false)
            .build();

        when(persistencePort.findPendingDocumentsToday(anyString(), any())).thenReturn(Flux.just(doc));
        when(persistencePort.lockDocumentForProcessing(anyLong(), anyInt())).thenReturn(Mono.just(1L));
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(file));
        when(documentValidator.validate(any(DocumentHistoryDTO.class), anyBoolean()))
            .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        
        when(s3Gateway.send(any(FileUploadRequest.class))).thenReturn(Mono.just(FileUploadResponse.builder()
            .success(true)
            .correlationId("corr-123")
            .build()));

        when(persistencePort.finalizeProcessingAtomically(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextMatches(FileUploadResponse::isSuccess)
            .expectComplete()
            .verify(Duration.ofSeconds(10));
    }

    @Test
    void executePendingDocuments_withS3_failure() {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-1")
            .productId("prod-1")
            .name("test.pdf")
            .retryCount(0)
            .isZip(false)
            .build();

        ProductDocumentFile file = ProductDocumentFile.builder()
            .productId("prod-1")
            .documentId("doc-1")
            .filename("test.pdf")
            .content(new byte[]{1})
            .isZip(false)
            .build();

        when(persistencePort.findPendingDocumentsToday(anyString(), any())).thenReturn(Flux.just(doc));
        when(persistencePort.lockDocumentForProcessing(anyLong(), anyInt())).thenReturn(Mono.just(1L));
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(file));
        when(documentValidator.validate(any(DocumentHistoryDTO.class), anyBoolean()))
            .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        
        when(s3Gateway.send(any(FileUploadRequest.class))).thenReturn(Mono.error(new RuntimeException("S3 connection error")));

        when(persistencePort.finalizeProcessingAtomically(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextMatches(result -> !result.isSuccess() && "S3 connection error".equals(result.getMessage()))
            .expectComplete()
            .verify(Duration.ofSeconds(10));
    }
}
