package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.HomologationResult;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.infrastructure.drivenadapters.soap.SoapErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SoapDocumentProcessingUseCaseTest {

    @Mock
    private DocumentPersistenceGateway persistencePort;

    @Mock
    private ProductRestGateway productRestGateway;

    @Mock
    private SoapGateway soapGateway;

    @Mock
    private HomologationRepository homologationRepository;

    @Mock
    private RulesBussinesGateway documentValidator;

    private SoapDocumentProcessingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SoapDocumentProcessingUseCase(
            persistencePort,
            productRestGateway,
            soapGateway,
            homologationRepository,
            documentValidator
        );
            
        lenient().when(persistencePort.finalizeProcessingAtomically(any()))
            .thenAnswer(invocation -> {
                com.example.fileprocessor.domain.entity.FinalizeProcessingCommand cmd = invocation.getArgument(0);
                return Mono.just(cmd.response());
            });
    }

    private static ProductDocumentHistory doc() {
        return ProductDocumentHistory.builder()
            .productId("prod-1")
            .isZip(false)
            .pais("AR")
            .documentId("doc-1")
            .name("test.pdf")
            .filename("test.pdf")
            .contentType("application/pdf")
            .size(1L)
            .origin("origin")
            .content(new byte[]{1})
            .build();
    }

    @Test
    void implementationName_returnsSOAP() {
        assertEquals("SOAP", useCase.implementationName());
    }

    @Test
    void uploadDocument_whenSuccess_returnsSuccessResult() {
        HomologationResult homologationResult = new HomologationResult("Manual de Origin", "Argentina");
        when(homologationRepository.resolve("origin", "AR")).thenReturn(Mono.just(homologationResult));

        FileUploadResponse successResult = FileUploadResponse.builder()
            .status(DocumentStatus.SUCCESS.name())
            .success(true)
            .correlationId("corr-456")
            .processedAt(Instant.now())
            .build();

        when(soapGateway.send(any(FileUploadRequest.class)))
            .thenReturn(Mono.just(successResult));

        StepVerifier.create(useCase.uploadDocument(doc(), "prod-1", 1L))
            .assertNext(result -> {
                assertTrue(result.isSuccess());
                assertEquals("corr-456", result.getCorrelationId());
            })
            .verifyComplete();
    }

    @Test
    void uploadDocument_whenError_returnsFailureResult() {
        HomologationResult homologationResult = new HomologationResult("Manual de Origin", "Argentina");
        when(homologationRepository.resolve("origin", "AR")).thenReturn(Mono.just(homologationResult));
        when(soapGateway.send(any(FileUploadRequest.class)))
            .thenReturn(Mono.error(new RuntimeException("SOAP error")));

        StepVerifier.create(useCase.uploadDocument(doc(), "prod-1", 1L))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(DocumentStatus.FAILURE.name(), result.getStatus());
            })
            .verifyComplete();
    }

    @Test
    void uploadDocument_homologationFails_propagatesError() {
        when(homologationRepository.resolve("origin", "AR"))
            .thenReturn(Mono.error(new RuntimeException("Origin not found")));

        StepVerifier.create(useCase.uploadDocument(doc(), "prod-1", 1L))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(DocumentStatus.FAILURE.name(), result.getStatus());
            })
            .verifyComplete();

        verify(soapGateway, never()).send(any());
    }

    @Test
    void uploadDocument_whenGatewayReturnsFailureStatus_propagatesFailure() {
        HomologationResult homologationResult = new HomologationResult("Manual de Origin", "Argentina");
        when(homologationRepository.resolve("origin", "AR")).thenReturn(Mono.just(homologationResult));

        FileUploadResponse failureResult = FileUploadResponse.builder()
            .status(DocumentStatus.FAILURE.name())
            .success(false)
            .errorCode(SoapErrorCodes.BAD_GATEWAY)
            .message("SOAP gateway error")
            .build();

        when(soapGateway.send(any(FileUploadRequest.class)))
            .thenReturn(Mono.just(failureResult));

        StepVerifier.create(useCase.uploadDocument(doc(), "prod-1", 1L))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(SoapErrorCodes.BAD_GATEWAY, result.getErrorCode());
            })
            .verifyComplete();
    }
}
