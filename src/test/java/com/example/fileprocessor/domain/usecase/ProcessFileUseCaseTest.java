package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.infrastructure.rest.dto.FileUploadResponseDto;
import com.example.fileprocessor.infrastructure.rest.mapper.FileMapper;
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
    private FileMapper fileMapper;

    @InjectMocks
    private ProcessFileUseCase processFileUseCase;

    @Test
    void execute_shouldReturnResponse_whenValidFile() {
        String traceId = UUID.randomUUID().toString();
        FileData fileData = new FileData(new byte[100], "test.pdf", 100,
            "application/pdf", traceId);
        SoapResponse expectedResponse = new SoapResponse("SUCCESS", "OK",
            "corr-123", traceId, Instant.now(), "ext-ref");
        FileUploadResponseDto dto = new FileUploadResponseDto("SUCCESS", "OK",
            "corr-123", traceId, Instant.now(), "ext-ref", true);

        when(fileValidator.validate(any(FileData.class))).thenReturn(Mono.just(fileData));
        when(soapGateway.sendFile(any(SoapRequest.class))).thenReturn(Mono.just(expectedResponse));
        when(fileMapper.toResponseDto(any(SoapResponse.class))).thenReturn(dto);

        StepVerifier.create(processFileUseCase.execute(fileData))
            .assertNext(response -> {
                assert response.success();
                assert response.correlationId().equals("corr-123");
            })
            .verifyComplete();

        verify(fileValidator, times(1)).validate(any(FileData.class));
        verify(soapGateway, times(1)).sendFile(any(SoapRequest.class));
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
    }

    @Test
    void execute_shouldHandleGatewayError() {
        String traceId = UUID.randomUUID().toString();
        FileData fileData = new FileData(new byte[100], "test.pdf", 100,
            "application/pdf", traceId);

        when(fileValidator.validate(any(FileData.class))).thenReturn(Mono.just(fileData));
        when(soapGateway.sendFile(any(SoapRequest.class)))
            .thenReturn(Mono.error(new RuntimeException("Gateway error")));

        StepVerifier.create(processFileUseCase.execute(fileData))
            .expectError(RuntimeException.class)
            .verify();
    }
}
