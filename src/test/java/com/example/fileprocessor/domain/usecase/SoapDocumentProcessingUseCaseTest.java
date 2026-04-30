package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SoapDocumentProcessingUseCaseTest {

    @Mock
    private ProductDocumentRepository documentRepository;
    @Mock
    private SoapGateway soapGateway;
    @Mock
    private ProductRestGateway productRestGateway;

    private SoapDocumentProcessingUseCase useCase;

    @BeforeEach
    void setUp() {
        FileValidator fileValidator = new FileValidator(10.0, "pdf,txt,csv");
        FolderInfoExtractor folderInfoExtractor = new FolderInfoExtractor(List.of("test", "mock"));
        useCase = new SoapDocumentProcessingUseCase(
            documentRepository, soapGateway,
            fileValidator, folderInfoExtractor,
            productRestGateway
        );
    }

    @Test
    void implementationName_shouldReturnSoap() {
        assertThat(useCase.implementationName()).isEqualTo("SOAP");
    }

    @Test
    void applyRulesMetadata_shouldRejectInvalidExtension() {
        ProductDocumentToProcess doc = ProductDocumentToProcess.builder()
            .documentId("doc1")
            .productId("prod1")
            .filename("document.exe")
            .content(new byte[]{1, 2, 3})
            .contentType("application/octet-stream")
            .status(DocumentStatus.PENDING.name())
            .fileSizeMb(0.5)
            .build();

        StepVerifier.create(useCase.applyRulesMetadata(doc))
            .expectErrorMatches(e -> e instanceof com.example.fileprocessor.domain.exception.FileValidationException)
            .verify();

        verify(documentRepository, never()).updateStatus(any(), any(), any(), any());
    }

    @Test
    void applyRulesMetadata_shouldAcceptValidExtension() {
        ProductDocumentToProcess doc = ProductDocumentToProcess.builder()
            .documentId("doc1")
            .productId("prod1")
            .filename("document.pdf")
            .content(new byte[]{1, 2, 3})
            .contentType("application/pdf")
            .status(DocumentStatus.PENDING.name())
            .fileSizeMb(0.5)
            .build();

        StepVerifier.create(useCase.applyRulesMetadata(doc))
            .assertNext(result -> {
                assertThat(result.documentId()).isEqualTo("doc1");
                assertThat(result.skipped()).isFalse();
            })
            .verifyComplete();
    }

    @Test
    void applyRulesMetadata_shouldRejectOversizedFile() {
        FileValidator smallFileValidator = new FileValidator(0.000001, "pdf,txt,csv");
        FolderInfoExtractor folderInfoExtractor = new FolderInfoExtractor(List.of("test", "mock"));
        SoapDocumentProcessingUseCase useCaseWithSmallLimit = new SoapDocumentProcessingUseCase(
            documentRepository, soapGateway, smallFileValidator, folderInfoExtractor, productRestGateway);

        ProductDocumentToProcess doc = ProductDocumentToProcess.builder()
            .documentId("doc1")
            .productId("prod1")
            .filename("document.pdf")
            .content(new byte[]{1, 2, 3})
            .contentType("application/pdf")
            .status(DocumentStatus.PENDING.name())
            .fileSizeMb(0.5)
            .build();

        StepVerifier.create(useCaseWithSmallLimit.applyRulesMetadata(doc))
            .expectErrorMatches(e -> e instanceof com.example.fileprocessor.domain.exception.FileValidationException)
            .verify();
    }

    @Test
    void uploadDocument_shouldReturnSuccessWhenSoapSucceeds() {
        FileUploadResult successResult = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .correlationId("corr-123")
            .processedAt(Instant.now())
            .success(true)
            .message("OK")
            .build();

        when(soapGateway.send(any(), any(), anyString(), anyString(), anyLong(), anyString(), anyString()))
            .thenReturn(Mono.just(successResult));

        DocumentToUpload docToUpload = new DocumentToUpload(
            ProductDocumentToProcess.builder()
                .documentId("doc1")
                .filename("doc.pdf")
                .content(new byte[]{1, 2, 3})
                .contentType("application/pdf")
                .fileSizeMb(0.5)
                .build(),
            new FolderInfo("parent", "child"),
            3L,
            false
        );

        StepVerifier.create(useCase.uploadDocument(docToUpload))
            .assertNext(result -> {
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getCorrelationId()).isEqualTo("corr-123");
            })
            .verifyComplete();
    }

    @Test
    void uploadDocument_shouldReturnFailureWhenSoapFails() {
        when(soapGateway.send(any(), any(), anyString(), anyString(), anyLong(), anyString(), anyString()))
            .thenReturn(Mono.error(new ProcessingException("SOAP error", "SOAP_ERROR", "trace-1")));

        DocumentToUpload docToUpload = new DocumentToUpload(
            ProductDocumentToProcess.builder()
                .documentId("doc1")
                .filename("doc.pdf")
                .content(new byte[]{1, 2, 3})
                .contentType("application/pdf")
                .fileSizeMb(0.5)
                .build(),
            new FolderInfo("parent", "child"),
            3L,
            false
        );

        StepVerifier.create(useCase.uploadDocument(docToUpload))
            .assertNext(result -> {
                assertThat(result.isSuccess()).isFalse();
                assertThat(result.getErrorCode()).isEqualTo("SOAP_ERROR");
            })
            .verifyComplete();
    }
}
