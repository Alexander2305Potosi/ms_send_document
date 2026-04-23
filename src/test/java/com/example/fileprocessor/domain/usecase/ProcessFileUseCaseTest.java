package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentInfo;
import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.SoapCommunicationLog;
import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.domain.port.out.DocumentRestGateway;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import com.example.fileprocessor.infrastructure.soap.exception.SoapCommunicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessFileUseCaseTest {

    @Mock
    private DocumentRestGateway documentGateway;

    @Mock
    private ExternalSoapGateway soapGateway;

    @Mock
    private FileValidator fileValidator;

    @Mock
    private SoapCommunicationLogRepository logRepository;

    @InjectMocks
    private ProcessFileUseCase processFileUseCase;

    @Test
    void execute_shouldReturnResponse_whenValidDocument() {
        String documentId = "doc-001";
        String traceId = UUID.randomUUID().toString();
        byte[] content = "test content".getBytes();

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
            .traceId(traceId)
            .build();

        SoapResponse expectedResponse = SoapResponse.builder()
            .status("SUCCESS")
            .message("OK")
            .correlationId("corr-123")
            .traceId(traceId)
            .processedAt(Instant.now())
            .externalReference("ext-ref")
            .build();

        SoapCommunicationLog logEntry = SoapCommunicationLog.builder()
            .traceId(traceId)
            .status("SUCCESS")
            .retryCount(0)
            .filename(documentId)
            .createdAt(Instant.now())
            .build();

        when(documentGateway.getDocument(eq(documentId), any(String.class)))
            .thenReturn(Mono.just(document));
        when(fileValidator.validate(any(FileData.class))).thenReturn(Mono.just(fileData));
        when(soapGateway.sendFile(any(SoapRequest.class))).thenReturn(Mono.just(expectedResponse));
        when(logRepository.save(any(SoapCommunicationLog.class))).thenReturn(Mono.just(logEntry));

        StepVerifier.create(processFileUseCase.execute(documentId, traceId))
            .assertNext(response -> {
                assert response.isSuccess();
                assert "corr-123".equals(response.getCorrelationId());
            })
            .verifyComplete();

        verify(documentGateway, times(1)).getDocument(eq(documentId), any(String.class));
        verify(fileValidator, times(1)).validate(any(FileData.class));
        verify(soapGateway, times(1)).sendFile(any(SoapRequest.class));
        verify(logRepository, times(1)).save(argThat(log ->
            log.getTraceId() != null && log.getStatus().equals("SUCCESS")));
    }

    @Test
    void execute_shouldPropagateValidationError() {
        String documentId = "doc-002";
        String traceId = UUID.randomUUID().toString();
        byte[] content = "test content".getBytes();

        DocumentInfo document = DocumentInfo.builder()
            .documentId(documentId)
            .filename("test.exe")
            .content(content)
            .contentType("application/x-exe")
            .size(content.length)
            .isZip(false)
            .build();

        FileData fileData = FileData.builder()
            .content(content)
            .filename("test.exe")
            .size(content.length)
            .contentType("application/x-exe")
            .traceId(traceId)
            .build();

        when(documentGateway.getDocument(eq(documentId), any(String.class)))
            .thenReturn(Mono.just(document));
        when(fileValidator.validate(any(FileData.class)))
            .thenReturn(Mono.error(new FileValidationException("Invalid type", "INVALID_FILE_TYPE")));

        StepVerifier.create(processFileUseCase.execute(documentId, traceId))
            .expectError(FileValidationException.class)
            .verify();

        verify(logRepository, never()).save(any(SoapCommunicationLog.class));
    }

    @Test
    void execute_shouldHandleGatewayError_andLogFailure() {
        String documentId = "doc-003";
        String traceId = UUID.randomUUID().toString();
        byte[] content = "test content".getBytes();

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
            .traceId(traceId)
            .build();

        SoapCommunicationException gatewayError = new SoapCommunicationException(
            "Gateway timeout", "GATEWAY_TIMEOUT", traceId);

        SoapCommunicationLog logEntry = SoapCommunicationLog.builder()
            .traceId(traceId)
            .status("FAILURE")
            .retryCount(0)
            .errorCode("GATEWAY_TIMEOUT")
            .filename(documentId)
            .createdAt(Instant.now())
            .build();

        when(documentGateway.getDocument(eq(documentId), any(String.class)))
            .thenReturn(Mono.just(document));
        when(fileValidator.validate(any(FileData.class))).thenReturn(Mono.just(fileData));
        when(soapGateway.sendFile(any(SoapRequest.class))).thenReturn(Mono.error(gatewayError));
        when(logRepository.save(any(SoapCommunicationLog.class))).thenReturn(Mono.just(logEntry));

        StepVerifier.create(processFileUseCase.execute(documentId, traceId))
            .expectErrorMatches(throwable -> throwable instanceof SoapCommunicationException &&
                ((SoapCommunicationException) throwable).getErrorCode().equals("GATEWAY_TIMEOUT"))
            .verify();

        verify(logRepository, times(1)).save(argThat(log ->
            log.getTraceId() != null && log.getStatus().equals("FAILURE")));
    }
}
