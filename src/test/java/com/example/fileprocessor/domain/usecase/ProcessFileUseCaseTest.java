package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentInfo;
import com.example.fileprocessor.domain.entity.DocumentToProcess;
import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.SoapCommunicationLog;
import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.domain.port.out.DocumentRestGateway;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import com.example.fileprocessor.infrastructure.soap.exception.SoapCommunicationException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessFileUseCaseTest {

    @Mock
    private DocumentRestGateway documentGateway;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ExternalSoapGateway soapGateway;

    @Mock
    private FileValidator fileValidator;

    @Mock
    private SoapCommunicationLogRepository logRepository;

    @Mock
    private FileValidationConfig validationConfig;

    private ProcessFileUseCase processFileUseCase;

    @BeforeEach
    void setUp() {
        processFileUseCase = new ProcessFileUseCase(
            documentGateway, documentRepository, soapGateway,
            fileValidator, logRepository, validationConfig);

        lenient().when(validationConfig.foldersToSkip()).thenReturn(List.of());
        lenient().when(validationConfig.maxFileSizeMb()).thenReturn(50);
        lenient().when(validationConfig.keywords()).thenReturn(List.of());
        lenient().when(documentRepository.claimDocument(any())).thenReturn(Mono.just(true));
    }

    @Test
    void executePendingDocuments_shouldProcessPendingDocuments() {
        String documentId = "doc-001";
        byte[] content = "test content".getBytes();

        DocumentToProcess pending = DocumentToProcess.builder()
            .documentId(documentId)
            .filename("test.pdf")
            .origin("/uploads/docs")
            .status("PENDING")
            .createdAt(Instant.now())
            .build();

        DocumentInfo document = DocumentInfo.builder()
            .documentId(documentId)
            .filename("test.pdf")
            .content(content)
            .contentType("application/pdf")
            .size(content.length)
            .isZip(false)
            .build();

        FileData fileData = FileData.builder()
            .content(content)
            .filename("test.pdf")
            .size(content.length)
            .contentType("application/pdf")
            .build();

        SoapResponse expectedResponse = SoapResponse.builder()
            .status("SUCCESS")
            .message("OK")
            .correlationId("corr-123")
            .traceId("trace-123")
            .processedAt(Instant.now())
            .externalReference("ext-ref")
            .build();

        SoapCommunicationLog logEntry = SoapCommunicationLog.builder()
            .traceId("trace-123")
            .status("SUCCESS")
            .retryCount(0)
            .filename("test.pdf")
            .createdAt(Instant.now())
            .build();

        when(documentRepository.findPendingDocuments()).thenReturn(Flux.just(pending));
        when(documentGateway.getDocument(eq(documentId), any(String.class))).thenReturn(Mono.just(document));
        when(fileValidator.validate(any(FileData.class))).thenReturn(Mono.just(fileData));
        when(soapGateway.sendFile(any(SoapRequest.class))).thenReturn(Mono.just(expectedResponse));
        when(logRepository.save(any(SoapCommunicationLog.class))).thenReturn(Mono.just(logEntry));
        when(documentRepository.updateStatus(any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(processFileUseCase.executePendingDocuments())
            .assertNext(response -> {
                assert response.isSuccess();
                assert "corr-123".equals(response.getCorrelationId());
            })
            .verifyComplete();

        verify(documentRepository).findPendingDocuments();
        verify(documentGateway).getDocument(eq(documentId), any(String.class));
        verify(fileValidator).validate(any(FileData.class));
        verify(soapGateway).sendFile(any(SoapRequest.class));
        verify(logRepository).save(any(SoapCommunicationLog.class));
    }

    @Test
    void executePendingDocuments_shouldHandleValidationError() {
        String documentId = "doc-002";
        byte[] content = "test content".getBytes();

        DocumentToProcess pending = DocumentToProcess.builder()
            .documentId(documentId)
            .filename("test.exe")
            .origin("/uploads/exe")
            .status("PENDING")
            .createdAt(Instant.now())
            .build();

        DocumentInfo document = DocumentInfo.builder()
            .documentId(documentId)
            .filename("test.exe")
            .content(content)
            .contentType("application/x-exe")
            .size(content.length)
            .isZip(false)
            .build();

        when(documentRepository.findPendingDocuments()).thenReturn(Flux.just(pending));
        when(documentGateway.getDocument(eq(documentId), any(String.class))).thenReturn(Mono.just(document));
        when(fileValidator.validate(any(FileData.class)))
            .thenReturn(Mono.error(new com.example.fileprocessor.domain.exception.FileValidationException("Invalid type", "INVALID_FILE_TYPE")));
        when(logRepository.save(any(SoapCommunicationLog.class))).thenReturn(Mono.empty());
        when(documentRepository.updateStatus(any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(processFileUseCase.executePendingDocuments())
            .expectErrorMatches(throwable -> throwable instanceof com.example.fileprocessor.domain.exception.FileValidationException)
            .verify();

        verify(documentRepository).updateStatus(eq(documentId), eq("FAILURE"), any(), isNull(), eq("INVALID_FILE_TYPE"));
    }

    @Test
    void executePendingDocuments_shouldHandleSoapError() {
        String documentId = "doc-003";
        byte[] content = "test content".getBytes();

        DocumentToProcess pending = DocumentToProcess.builder()
            .documentId(documentId)
            .filename("test.pdf")
            .origin("/uploads/docs")
            .status("PENDING")
            .createdAt(Instant.now())
            .build();

        DocumentInfo document = DocumentInfo.builder()
            .documentId(documentId)
            .filename("test.pdf")
            .content(content)
            .contentType("application/pdf")
            .size(content.length)
            .isZip(false)
            .build();

        FileData fileData = FileData.builder()
            .content(content)
            .filename("test.pdf")
            .size(content.length)
            .contentType("application/pdf")
            .build();

        SoapCommunicationException gatewayError = new SoapCommunicationException(
            "Gateway timeout", "GATEWAY_TIMEOUT", "trace-123");

        SoapCommunicationLog logEntry = SoapCommunicationLog.builder()
            .traceId("trace-123")
            .status("FAILURE")
            .retryCount(0)
            .errorCode("GATEWAY_TIMEOUT")
            .filename("test.pdf")
            .createdAt(Instant.now())
            .build();

        when(documentRepository.findPendingDocuments()).thenReturn(Flux.just(pending));
        when(documentGateway.getDocument(eq(documentId), any(String.class))).thenReturn(Mono.just(document));
        when(fileValidator.validate(any(FileData.class))).thenReturn(Mono.just(fileData));
        when(soapGateway.sendFile(any(SoapRequest.class))).thenReturn(Mono.error(gatewayError));
        when(logRepository.save(any(SoapCommunicationLog.class))).thenReturn(Mono.just(logEntry));
        when(documentRepository.updateStatus(any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(processFileUseCase.executePendingDocuments())
            .expectErrorMatches(throwable -> throwable instanceof SoapCommunicationException &&
                ((SoapCommunicationException) throwable).getErrorCode().equals("GATEWAY_TIMEOUT"))
            .verify();

        verify(logRepository).save(any(SoapCommunicationLog.class));
    }

    @Test
    void executePendingDocuments_shouldSkipByFolder() {
        String documentId = "doc-004";

        DocumentToProcess pending = DocumentToProcess.builder()
            .documentId(documentId)
            .filename("test.pdf")
            .origin("/skip/folder")
            .status("PENDING")
            .createdAt(Instant.now())
            .build();

        when(validationConfig.foldersToSkip()).thenReturn(List.of("skip"));
        when(documentRepository.findPendingDocuments()).thenReturn(Flux.just(pending));
        when(documentRepository.updateStatus(any(), any(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(processFileUseCase.executePendingDocuments())
            .assertNext(response -> {
                assert response.isSuccess();
                assert "SKIPPED".equals(response.getStatus());
            })
            .verifyComplete();

        verify(documentGateway, never()).getDocument(any(), any());
    }

    @Test
    void executePendingDocuments_shouldSkipBySize() {
        String documentId = "doc-005";
        byte[] content = new byte[60 * 1024 * 1024]; // 60MB

        DocumentToProcess pending = DocumentToProcess.builder()
            .documentId(documentId)
            .filename("large.pdf")
            .origin("/uploads/docs")
            .status("PENDING")
            .createdAt(Instant.now())
            .build();

        DocumentInfo document = DocumentInfo.builder()
            .documentId(documentId)
            .filename("large.pdf")
            .content(content)
            .contentType("application/pdf")
            .size(content.length)
            .isZip(false)
            .build();

        when(documentRepository.findPendingDocuments()).thenReturn(Flux.just(pending));
        when(documentGateway.getDocument(eq(documentId), any(String.class))).thenReturn(Mono.just(document));
        lenient().when(validationConfig.maxFileSizeMb()).thenReturn(50);
        lenient().when(fileValidator.validate(any(FileData.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(processFileUseCase.executePendingDocuments())
            .assertNext(response -> {
                assert response.isSuccess();
                assert "SKIPPED".equals(response.getStatus());
            })
            .verifyComplete();

        verify(soapGateway, never()).sendFile(any());
    }
}
