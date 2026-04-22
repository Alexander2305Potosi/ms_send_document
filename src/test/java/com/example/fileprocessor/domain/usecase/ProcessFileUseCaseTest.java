package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.domain.entity.SoapCommunicationLog;
import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import com.example.fileprocessor.infrastructure.soap.exception.SoapCommunicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessFileUseCaseTest {

    @Mock
    private ExternalSoapGateway soapGateway;

    @Mock
    private FileValidator fileValidator;

    @Mock
    private SoapCommunicationLogRepository logRepository;

    @InjectMocks
    private ProcessFileUseCase processFileUseCase;

    @Test
    void execute_shouldReturnResponse_whenValidFile() {
        String traceId = UUID.randomUUID().toString();
        FileData fileData = new FileData(new byte[100], "test.pdf", 100,
            "application/pdf", traceId);
        SoapResponse expectedResponse = new SoapResponse("SUCCESS", "OK",
            "corr-123", traceId, Instant.now(), "ext-ref");

        when(fileValidator.validate(any(FileData.class))).thenReturn(Mono.just(fileData));
        when(soapGateway.sendFile(any(SoapRequest.class))).thenReturn(Mono.just(expectedResponse));
        when(logRepository.save(any(SoapCommunicationLog.class))).thenReturn(Mono.just(new SoapCommunicationLog(
            traceId, "SUCCESS", 0, null, "test.pdf", Instant.now())));

        StepVerifier.create(processFileUseCase.execute(fileData))
            .assertNext(response -> {
                assert response.success();
                assert response.correlationId().equals("corr-123");
            })
            .verifyComplete();

        verify(fileValidator, times(1)).validate(any(FileData.class));
        verify(soapGateway, times(1)).sendFile(any(SoapRequest.class));
        verify(logRepository, times(1)).save(argThat(log ->
            log.traceId().equals(traceId) && log.status().equals("SUCCESS")));
    }

    @Test
    void execute_shouldPropagateValidationError() {
        String traceId = UUID.randomUUID().toString();
        FileData fileData = new FileData(new byte[100], "test.exe", 100,
            "application/x-exe", traceId);

        when(fileValidator.validate(any(FileData.class)))
            .thenReturn(Mono.error(new FileValidationException("Invalid type", "INVALID_FILE_TYPE")));

        StepVerifier.create(processFileUseCase.execute(fileData))
            .expectError(FileValidationException.class)
            .verify();

        verify(logRepository, never()).save(any(SoapCommunicationLog.class));
    }

    @Test
    void execute_shouldHandleGatewayError_andLogFailure() {
        String traceId = UUID.randomUUID().toString();
        FileData fileData = new FileData(new byte[100], "test.pdf", 100,
            "application/pdf", traceId);
        SoapCommunicationException gatewayError = new SoapCommunicationException(
            "Gateway timeout", "GATEWAY_TIMEOUT", traceId);

        when(fileValidator.validate(any(FileData.class))).thenReturn(Mono.just(fileData));
        when(soapGateway.sendFile(any(SoapRequest.class))).thenReturn(Mono.error(gatewayError));
        when(logRepository.save(any(SoapCommunicationLog.class))).thenReturn(Mono.just(new SoapCommunicationLog(
            traceId, "FAILURE", 0, "GATEWAY_TIMEOUT", "test.pdf", Instant.now())));

        StepVerifier.create(processFileUseCase.execute(fileData))
            .expectErrorMatches(throwable -> throwable instanceof SoapCommunicationException &&
                ((SoapCommunicationException) throwable).getErrorCode().equals("GATEWAY_TIMEOUT"))
            .verify();

        verify(logRepository, times(1)).save(argThat(log ->
            log.traceId().equals(traceId) && log.status().equals("FAILURE")));
    }
}
