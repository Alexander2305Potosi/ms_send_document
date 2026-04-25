package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentInfo;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipOutputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessProductDocumentsUseCaseTest {

    @Mock
    private ProductRestGateway productGateway;

    @Mock
    private ProductDocumentRepository documentRepository;

    @Mock
    private ExternalSoapGateway soapGateway;

    @Mock
    private FileValidator fileValidator;

    @Mock
    private SoapCommunicationLogRepository logRepository;

    @Mock
    private FileValidationConfig validationConfig;

    private ProcessProductDocumentsUseCase useCase;

    @BeforeEach
    void setUp() {
        lenient().when(validationConfig.foldersToSkip()).thenReturn(List.of());
        lenient().when(validationConfig.keywords()).thenReturn(List.of());
        lenient().when(validationConfig.maxFileSizeMb()).thenReturn(50);

        useCase = new ProcessProductDocumentsUseCase(
            productGateway,
            documentRepository,
            soapGateway,
            fileValidator,
            logRepository,
            validationConfig
        );
    }

    @Test
    void executePendingDocuments_shouldProcessDocumentsPerDocument() {
        ProductDocumentToProcess doc1 = ProductDocumentToProcess.builder()
            .documentId("doc-001")
            .productId("prod-001")
            .filename("manual.pdf")
            .origin("folderA/incoming")
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .build();

        ProductDocumentToProcess doc2 = ProductDocumentToProcess.builder()
            .documentId("doc-002")
            .productId("prod-001")
            .filename("specs.pdf")
            .origin("folderB/incoming")
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .build();

        ProductDocumentInfo docInfo1 = new ProductDocumentInfo(
            "doc-001", "manual.pdf", new byte[]{1, 2, 3}, "application/pdf", 1024, false, "folderA/incoming"
        );
        ProductDocumentInfo docInfo2 = new ProductDocumentInfo(
            "doc-002", "specs.pdf", new byte[]{4, 5, 6}, "application/pdf", 2048, false, "folderB/incoming"
        );

        SoapResponse soapResponse1 = SoapResponse.builder()
            .status(DocumentStatus.SUCCESS_VALUE)
            .message("Success")
            .correlationId("corr-001")
            .processedAt(Instant.now())
            .build();

        SoapResponse soapResponse2 = SoapResponse.builder()
            .status(DocumentStatus.SUCCESS_VALUE)
            .message("Success")
            .correlationId("corr-002")
            .processedAt(Instant.now())
            .build();

        FileUploadResult result1 = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS_VALUE)
            .message("Success")
            .correlationId("corr-001")
            .processedAt(Instant.now())
            .success(true)
            .build();

        FileUploadResult result2 = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS_VALUE)
            .message("Success")
            .correlationId("corr-002")
            .processedAt(Instant.now())
            .success(true)
            .build();

        when(documentRepository.findPendingDocuments()).thenReturn(Flux.just(doc1, doc2));
        when(documentRepository.claimDocument("doc-001")).thenReturn(Mono.just(true));
        when(documentRepository.claimDocument("doc-002")).thenReturn(Mono.just(true));
        when(productGateway.getDocument(anyString(), anyString(), anyString())).thenReturn(Mono.just(docInfo1), Mono.just(docInfo2));

        FileData fileData1 = FileData.builder()
            .filename("manual.pdf")
            .content(new byte[]{1, 2, 3})
            .size(1024)
            .contentType("application/pdf")
            .traceId("trace-001")
            .build();

        FileData fileData2 = FileData.builder()
            .filename("specs.pdf")
            .content(new byte[]{4, 5, 6})
            .size(2048)
            .contentType("application/pdf")
            .traceId("trace-002")
            .build();

        when(fileValidator.validate(any(FileData.class))).thenReturn(Mono.just(fileData1), Mono.just(fileData2));
        when(soapGateway.sendFile(any())).thenReturn(Mono.just(soapResponse1), Mono.just(soapResponse2));
        when(documentRepository.updateStatus(anyString(), anyString(), anyString(), nullable(String.class), nullable(String.class)))
            .thenReturn(Mono.empty());
        when(logRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextCount(2)
            .verifyComplete();

        verify(documentRepository).claimDocument("doc-001");
        verify(documentRepository).claimDocument("doc-002");
    }

    @Test
    void executePendingDocuments_shouldSkipAlreadyClaimedDocuments() {
        ProductDocumentToProcess doc1 = ProductDocumentToProcess.builder()
            .documentId("doc-001")
            .productId("prod-001")
            .filename("manual.pdf")
            .origin("folderA/incoming")
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .build();

        when(documentRepository.findPendingDocuments()).thenReturn(Flux.just(doc1));
        when(documentRepository.claimDocument("doc-001")).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.executePendingDocuments())
            .verifyComplete();

        verify(productGateway, never()).getDocument(anyString(), anyString(), anyString());
    }

    @Test
    void executePendingDocuments_shouldHandleEmptyQueue() {
        when(documentRepository.findPendingDocuments()).thenReturn(Flux.empty());

        StepVerifier.create(useCase.executePendingDocuments())
            .verifyComplete();

        verify(documentRepository, never()).claimDocument(anyString());
    }

    @Test
    void executePendingDocuments_shouldSkipDocumentsByFolder() {
        ProductDocumentToProcess doc1 = ProductDocumentToProcess.builder()
            .documentId("doc-001")
            .productId("prod-001")
            .filename("manual.pdf")
            .origin("/tmp/incoming")
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .build();

        when(validationConfig.foldersToSkip()).thenReturn(List.of("/tmp"));

        when(documentRepository.findPendingDocuments()).thenReturn(Flux.just(doc1));
        when(documentRepository.claimDocument("doc-001")).thenReturn(Mono.just(true));
        when(documentRepository.updateStatus(anyString(), anyString(), anyString(), nullable(String.class), nullable(String.class)))
            .thenReturn(Mono.empty());

        StepVerifier.create(useCase.executePendingDocuments())
            .assertNext(result -> {
                assert result.getStatus().equals(DocumentStatus.SKIPPED_VALUE);
                assert result.isSuccess();
            })
            .verifyComplete();

        verify(productGateway, never()).getDocument(anyString(), anyString(), anyString());
    }

    @Test
    void executePendingDocuments_shouldProcessZipDocument() throws Exception {
        ProductDocumentToProcess doc1 = ProductDocumentToProcess.builder()
            .documentId("doc-001")
            .productId("prod-001")
            .filename("documents.zip")
            .origin("folderA/incoming")
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .build();

        // Create a valid ZIP using ZipOutputStream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new java.util.zip.ZipEntry("test.txt"));
            zos.write("hello".getBytes());
            zos.closeEntry();
        }
        byte[] zipContent = baos.toByteArray();

        ProductDocumentInfo docInfo1 = new ProductDocumentInfo(
            "doc-001", "documents.zip", zipContent, "application/zip", zipContent.length, true, "folderA/incoming"
        );

        when(documentRepository.findPendingDocuments()).thenReturn(Flux.just(doc1));
        when(documentRepository.claimDocument("doc-001")).thenReturn(Mono.just(true));
        when(productGateway.getDocument(anyString(), anyString(), anyString())).thenReturn(Mono.just(docInfo1));
        when(documentRepository.updateStatus(anyString(), anyString(), anyString(), nullable(String.class), nullable(String.class)))
            .thenReturn(Mono.empty());
        when(logRepository.save(any())).thenReturn(Mono.empty());

        // Stub validate and SOAP send for the extracted file inside ZIP
        when(fileValidator.validate(any(FileData.class))).thenAnswer(invocation -> {
            FileData input = invocation.getArgument(0);
            return Mono.just(input);
        });
        when(soapGateway.sendFile(any())).thenReturn(Mono.just(SoapResponse.builder()
            .status(DocumentStatus.SUCCESS_VALUE)
            .message("Success")
            .correlationId("corr-001")
            .processedAt(Instant.now())
            .build()));

        // ZIP processing returns aggregated result
        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextCount(1)
            .verifyComplete();
    }

    @Test
    void executePendingDocuments_shouldNotReprocessAlreadySuccessfulDocuments() {
        // Scenario: MS crashed after doc-001 was processed successfully
        // When restarted, findPendingDocuments returns doc-001 (PROCESSING) and doc-002 (PENDING)
        // claimDocument for doc-001 should return FALSE because it's PROCESSING (not PENDING)
        // So doc-001 should NOT be reprocessed - only doc-002 should be claimed and processed

        ProductDocumentToProcess docProcessing = ProductDocumentToProcess.builder()
            .documentId("doc-001")
            .productId("prod-001")
            .filename("manual.pdf")
            .origin("folderA/incoming")
            .status(DocumentStatus.PROCESSING_VALUE) // Orphan - was PROCESSING when crash occurred
            .createdAt(Instant.now())
            .build();

        ProductDocumentToProcess docPending = ProductDocumentToProcess.builder()
            .documentId("doc-002")
            .productId("prod-001")
            .filename("specs.pdf")
            .origin("folderB/incoming")
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .build();

        ProductDocumentInfo docInfo2 = new ProductDocumentInfo(
            "doc-002", "specs.pdf", new byte[]{4, 5, 6}, "application/pdf", 2048, false, "folderB/incoming"
        );

        SoapResponse soapResponse = SoapResponse.builder()
            .status(DocumentStatus.SUCCESS_VALUE)
            .message("Success")
            .correlationId("corr-002")
            .processedAt(Instant.now())
            .build();

        FileData fileData2 = FileData.builder()
            .filename("specs.pdf")
            .content(new byte[]{4, 5, 6})
            .size(2048)
            .contentType("application/pdf")
            .traceId("trace-002")
            .build();

        // findPendingDocuments returns both PROCESSING and PENDING documents
        when(documentRepository.findPendingDocuments()).thenReturn(Flux.just(docProcessing, docPending));

        // claimDocument returns FALSE for doc-001 because it's PROCESSING (not PENDING)
        // claimDocument returns TRUE for doc-002 because it's PENDING
        when(documentRepository.claimDocument("doc-001")).thenReturn(Mono.just(false));
        when(documentRepository.claimDocument("doc-002")).thenReturn(Mono.just(true));

        // Only doc-002 should be fetched from gateway
        when(productGateway.getDocument(anyString(), anyString(), anyString())).thenReturn(Mono.just(docInfo2));

        when(fileValidator.validate(any(FileData.class))).thenReturn(Mono.just(fileData2));
        when(soapGateway.sendFile(any())).thenReturn(Mono.just(soapResponse));
        when(documentRepository.updateStatus(anyString(), anyString(), anyString(), nullable(String.class), nullable(String.class)))
            .thenReturn(Mono.empty());
        when(logRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextCount(1) // Only doc-002 should be processed
            .verifyComplete();

        // Verify that doc-001 was NOT reprocessed (no gateway call)
        verify(productGateway, never()).getDocument(eq("prod-001"), eq("doc-001"), anyString());
        // Verify that doc-002 WAS processed
        verify(productGateway).getDocument(eq("prod-001"), eq("doc-002"), anyString());
    }
}
