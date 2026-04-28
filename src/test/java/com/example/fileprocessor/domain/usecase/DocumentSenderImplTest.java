package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.CommunicationLog;
import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.port.out.CommunicationLogRepository;
import com.example.fileprocessor.domain.port.out.FileGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentSenderImplTest {

    @Mock
    private FileGateway fileGateway;

    @Mock
    private CommunicationLogRepository logRepository;

    private DocumentSenderImpl sender;

    @BeforeEach
    void setUp() {
        sender = new DocumentSenderImpl(fileGateway, logRepository);
    }

    @Test
    void send_shouldReturnResultAndSaveLogOnSuccess() {
        DocumentSendRequest request = createRequest("doc-1", "trace-123");
        FileUploadResult uploadResult = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .correlationId("corr-456")
            .success(true)
            .build();

        when(fileGateway.send(request)).thenReturn(Mono.just(uploadResult));
        when(logRepository.save(any(CommunicationLog.class))).thenReturn(Mono.just(CommunicationLog.builder().build()));

        StepVerifier.create(sender.send(request))
            .expectNextMatches(result -> {
                assert result.isSuccess();
                assert "corr-456".equals(result.getCorrelationId());
                return true;
            })
            .verifyComplete();

        verify(fileGateway).send(request);
        verify(logRepository).save(any(CommunicationLog.class));
    }

    @Test
    void send_shouldReturnFailureResultWhenUploadFails() {
        DocumentSendRequest request = createRequest("doc-2", "trace-456");
        FileUploadResult failureResult = FileUploadResult.builder()
            .status(DocumentStatus.FAILURE.name())
            .errorCode("SERVICE_ERROR")
            .success(false)
            .build();

        when(fileGateway.send(request)).thenReturn(Mono.just(failureResult));
        when(logRepository.save(any(CommunicationLog.class))).thenReturn(Mono.just(CommunicationLog.builder().build()));

        StepVerifier.create(sender.send(request))
            .expectNextMatches(result -> {
                assert !result.isSuccess();
                assert "SERVICE_ERROR".equals(result.getErrorCode());
                return true;
            })
            .verifyComplete();

        verify(fileGateway).send(request);
        verify(logRepository).save(any(CommunicationLog.class));
    }

    @Test
    void send_shouldPropagateGatewayError() {
        DocumentSendRequest request = createRequest("doc-3", "trace-789");

        when(fileGateway.send(request)).thenReturn(Mono.error(new RuntimeException("Gateway error")));

        StepVerifier.create(sender.send(request))
            .expectError(RuntimeException.class)
            .verify();

        verify(logRepository, never()).save(any());
    }

    private DocumentSendRequest createRequest(String docId, String traceId) {
        return DocumentSendRequest.builder()
            .documentId(docId)
            .fileContent(new byte[]{1, 2, 3})
            .filename("test.pdf")
            .contentType("application/pdf")
            .fileSize(3)
            .traceId(traceId)
            .parentFolder("incoming")
            .childFolder(null)
            .build();
    }
}
