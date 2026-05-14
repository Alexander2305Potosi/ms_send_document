package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.HomologationResult;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
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
    private RulesBussinesGateway documentValidator;

    @Mock
    private HomologationRepository homologationRepository;

    private SoapDocumentProcessingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SoapDocumentProcessingUseCase(
            persistencePort,
            productRestGateway,
            soapGateway,
            documentValidator,
            homologationRepository
        );

        lenient().when(homologationRepository.resolve(anyString(), anyString()))
            .thenReturn(Mono.just(new HomologationResult("homo-origin", "homo-pais")));
            
        lenient().when(persistencePort.finalizeProcessingAtomically(any()))
            .thenAnswer(invocation -> {
                com.example.fileprocessor.domain.entity.DocumentUpdateCommand cmd = invocation.getArgument(0);
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
        FileUploadResponse successResult = FileUploadResponse.builder()
            .status(ProcessingResultCodes.SUCCESS.name())
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
        when(soapGateway.send(any(FileUploadRequest.class)))
            .thenReturn(Mono.error(new RuntimeException("SOAP error")));

        StepVerifier.create(useCase.uploadDocument(doc(), "prod-1", 1L))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(ProcessingResultCodes.FAILURE.name(), result.getStatus());
            })
            .verifyComplete();
    }

    @Test
    void uploadDocument_whenGatewayReturnsFailureStatus_propagatesFailure() {
        FileUploadResponse failureResult = FileUploadResponse.builder()
            .status(ProcessingResultCodes.FAILURE.name())
            .success(false)
            .errorCode(ProcessingResultCodes.BAD_GATEWAY.name())
            .message("SOAP gateway error")
            .build();

        when(soapGateway.send(any(FileUploadRequest.class)))
            .thenReturn(Mono.just(failureResult));

        StepVerifier.create(useCase.uploadDocument(doc(), "prod-1", 1L))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(ProcessingResultCodes.BAD_GATEWAY.name(), result.getErrorCode());
            })
            .verifyComplete();
    }
}
