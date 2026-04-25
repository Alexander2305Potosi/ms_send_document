package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessProductDocumentsUseCaseTest {

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
        lenient().when(validationConfig.originPatternsToSend()).thenReturn(List.of("incoming", "documents"));

        useCase = new ProcessProductDocumentsUseCase(
            documentRepository,
            soapGateway,
            fileValidator,
            logRepository,
            validationConfig
        );
    }

    @Test
    void executePendingDocuments_shouldProcessDocumentsPerDocument() {
        // Files must be < 50MB to be sent to SOAP
        byte[] smallContent1 = new byte[40 * 1024 * 1024]; // 40MB
        byte[] smallContent2 = new byte[35 * 1024 * 1024]; // 35MB

        ProductDocumentToProcess doc1 = ProductDocumentToProcess.builder()
            .documentId("doc-001")
            .productId("prod-001")
            .filename("manual.pdf")
            .content(smallContent1)
            .contentType("application/pdf")
            .origin("folderA/incoming")
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .build();

        ProductDocumentToProcess doc2 = ProductDocumentToProcess.builder()
            .documentId("doc-002")
            .productId("prod-001")
            .filename("specs.pdf")
            .content(smallContent2)
            .contentType("application/pdf")
            .origin("folderB/incoming")
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .build();

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

        when(documentRepository.findPendingDocuments()).thenReturn(Flux.just(doc1, doc2));
        when(documentRepository.claimDocument("doc-001")).thenReturn(Mono.just(true));
        when(documentRepository.claimDocument("doc-002")).thenReturn(Mono.just(true));
        when(fileValidator.validate(any(FileData.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
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
            .content(new byte[]{1, 2, 3})
            .contentType("application/pdf")
            .origin("folderA/incoming")
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .build();

        when(documentRepository.findPendingDocuments()).thenReturn(Flux.just(doc1));
        when(documentRepository.claimDocument("doc-001")).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.executePendingDocuments())
            .verifyComplete();

        verify(soapGateway, never()).sendFile(any());
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
        // File must be < 50MB to pass size check, then folder rule applies
        byte[] smallContent = new byte[40 * 1024 * 1024]; // 40MB

        ProductDocumentToProcess doc1 = ProductDocumentToProcess.builder()
            .documentId("doc-001")
            .productId("prod-001")
            .filename("manual.pdf")
            .content(smallContent)
            .contentType("application/pdf")
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

        verify(soapGateway, never()).sendFile(any());
    }

    @Test
    void executePendingDocuments_shouldNotSendFilesLargerThan50MB() {
        // File with size >= 50MB should be marked as NOT_SENT
        byte[] largeContent = new byte[55 * 1024 * 1024]; // 55MB

        ProductDocumentToProcess doc1 = ProductDocumentToProcess.builder()
            .documentId("doc-001")
            .productId("prod-001")
            .filename("large-file.pdf")
            .content(largeContent)
            .contentType("application/pdf")
            .origin("folderA/incoming")
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .build();

        when(documentRepository.findPendingDocuments()).thenReturn(Flux.just(doc1));
        when(documentRepository.claimDocument("doc-001")).thenReturn(Mono.just(true));
        when(documentRepository.updateStatus(anyString(), anyString(), anyString(), nullable(String.class), nullable(String.class)))
            .thenReturn(Mono.empty());

        StepVerifier.create(useCase.executePendingDocuments())
            .assertNext(result -> {
                assert result.getStatus().equals(DocumentStatus.NOT_SENT_VALUE);
                assert result.getMessage().contains("file size");
                assert result.isSuccess();
            })
            .verifyComplete();

        verify(soapGateway, never()).sendFile(any());
    }

    @Test
    void executePendingDocuments_shouldNotReprocessAlreadySuccessfulDocuments() {
        // Scenario: MS crashed after doc-001 was processed successfully
        // When restarted, findPendingDocuments returns doc-001 (PROCESSING) and doc-002 (PENDING)
        // claimDocument for doc-001 should return FALSE because it's PROCESSING (not PENDING)
        // So doc-001 should NOT be reprocessed - only doc-002 should be claimed and processed

        byte[] smallContent = new byte[40 * 1024 * 1024]; // 40MB (< 50MB, should be sent)

        ProductDocumentToProcess docProcessing = ProductDocumentToProcess.builder()
            .documentId("doc-001")
            .productId("prod-001")
            .filename("manual.pdf")
            .content(smallContent)
            .contentType("application/pdf")
            .origin("folderA/incoming")
            .status(DocumentStatus.PROCESSING_VALUE)
            .createdAt(Instant.now())
            .build();

        ProductDocumentToProcess docPending = ProductDocumentToProcess.builder()
            .documentId("doc-002")
            .productId("prod-001")
            .filename("specs.pdf")
            .content(smallContent) // < 50MB so it gets sent
            .contentType("application/pdf")
            .origin("folderB/incoming")
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .build();

        SoapResponse soapResponse = SoapResponse.builder()
            .status(DocumentStatus.SUCCESS_VALUE)
            .message("Success")
            .correlationId("corr-002")
            .processedAt(Instant.now())
            .build();

        when(documentRepository.findPendingDocuments()).thenReturn(Flux.just(docProcessing, docPending));
        when(documentRepository.claimDocument("doc-001")).thenReturn(Mono.just(false));
        when(documentRepository.claimDocument("doc-002")).thenReturn(Mono.just(true));
        when(fileValidator.validate(any(FileData.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(soapGateway.sendFile(any())).thenReturn(Mono.just(soapResponse));
        when(documentRepository.updateStatus(anyString(), anyString(), anyString(), nullable(String.class), nullable(String.class)))
            .thenReturn(Mono.empty());
        when(logRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextCount(1)
            .verifyComplete();

        // doc-001 was skipped (claim returned false), doc-002 was processed
        // So sendFile should be called exactly once (for doc-002 only)
        verify(soapGateway, times(1)).sendFile(any());
    }

    @Test
    void executePendingDocuments_shouldSendFilesSmallerThan50MB() {
        // File with size < 50MB should be sent to SOAP
        byte[] smallContent = new byte[40 * 1024 * 1024]; // 40MB

        ProductDocumentToProcess doc1 = ProductDocumentToProcess.builder()
            .documentId("doc-001")
            .productId("prod-001")
            .filename("small-file.pdf")
            .content(smallContent)
            .contentType("application/pdf")
            .origin("folderA/incoming")
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .build();

        SoapResponse soapResponse = SoapResponse.builder()
            .status(DocumentStatus.SUCCESS_VALUE)
            .message("Success")
            .correlationId("corr-001")
            .processedAt(Instant.now())
            .build();

        when(documentRepository.findPendingDocuments()).thenReturn(Flux.just(doc1));
        when(documentRepository.claimDocument("doc-001")).thenReturn(Mono.just(true));
        when(fileValidator.validate(any(FileData.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(soapGateway.sendFile(any())).thenReturn(Mono.just(soapResponse));
        when(documentRepository.updateStatus(anyString(), anyString(), anyString(), nullable(String.class), nullable(String.class)))
            .thenReturn(Mono.empty());
        when(logRepository.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextCount(1)
            .verifyComplete();

        // File < 50MB should be sent
        verify(soapGateway, times(1)).sendFile(any());
    }

    @Test
    void executePendingDocuments_shouldNotSendIfOriginDoesNotMatchPattern() {
        // Origin "other/folder" doesn't match patterns (incoming, documents)
        byte[] smallContent = new byte[40 * 1024 * 1024]; // 40MB

        ProductDocumentToProcess doc1 = ProductDocumentToProcess.builder()
            .documentId("doc-001")
            .productId("prod-001")
            .filename("manual.pdf")
            .content(smallContent)
            .contentType("application/pdf")
            .origin("other/folder")
            .status(DocumentStatus.PENDING_VALUE)
            .createdAt(Instant.now())
            .build();

        when(documentRepository.findPendingDocuments()).thenReturn(Flux.just(doc1));
        when(documentRepository.claimDocument("doc-001")).thenReturn(Mono.just(true));
        when(documentRepository.updateStatus(anyString(), anyString(), anyString(), nullable(String.class), nullable(String.class)))
            .thenReturn(Mono.empty());

        StepVerifier.create(useCase.executePendingDocuments())
            .assertNext(result -> {
                assert result.getStatus().equals(DocumentStatus.NOT_SENT_VALUE);
                assert result.getMessage().contains("origin does not match");
                assert result.isSuccess();
            })
            .verifyComplete();

        verify(soapGateway, never()).sendFile(any());
    }
}
