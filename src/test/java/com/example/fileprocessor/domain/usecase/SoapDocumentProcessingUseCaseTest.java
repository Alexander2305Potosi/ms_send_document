package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.homologation.HomologationResult;
import com.example.fileprocessor.domain.entity.product.maestro.ProductDocumentFile;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

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
        useCase = new SoapDocumentProcessingUseCase(persistencePort, productRestGateway, soapGateway, documentValidator, homologationRepository, "/tmp/test-zip-dir");
    }

    @Test
    void executePendingDocuments_withSoap_success() {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-1")
            .productId("prod-1")
            .name("test.pdf")
            .retryCount(0)
            .isZip(false)
            .build();

        ProductDocumentFile file = ProductDocumentFile.builder()
            .productId("prod-1")
            .documentId("doc-1")
            .filename("test.pdf")
            .content(new byte[]{1})
            .originFolder("origin")
            .originCountry("AR")
            .isZip(false)
            .build();

        when(persistencePort.findPendingDocumentsToday(anyString(), any())).thenReturn(Flux.just(doc));
        when(persistencePort.lockDocumentForProcessing(anyLong(), anyInt())).thenReturn(Mono.just(1L));
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(file));
        when(documentValidator.validate(any(DocumentHistoryDTO.class), anyBoolean()))
            .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        
        when(homologationRepository.resolve(any()))
            .thenReturn(Mono.just(HomologationResult.builder()
                .categoriaDocument("mocked-categoria")
                .homologationCountry(com.example.fileprocessor.domain.entity.homologation.HomologationCountry.builder()
                    .homologationFolder("homo-folder")
                    .homologationCountry("homo-country")
                    .build())
                .build()));

        when(soapGateway.send(any(FileUploadRequest.class))).thenReturn(Flux.just(FileUploadResponse.builder()
            .success(true)
            .correlationId("soap-corr-123")
            .build()));

        when(persistencePort.finalizeProcessingAtomically(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextMatches(FileUploadResponse::isSuccess)
            .expectComplete()
            .verify(Duration.ofSeconds(10));
    }

    @Test
    void uploadDocument_whenSoapGatewayThrowsException_returnsFailureResponse() {
        DocumentHistoryDTO history = DocumentHistoryDTO.builder()
                .filename("test.pdf")
                .build();
        
        when(homologationRepository.resolve(any()))
                .thenReturn(Mono.just(HomologationResult.builder()
                        .categoriaDocument("mocked-categoria")
                        .build()));

        when(soapGateway.send(any(FileUploadRequest.class)))
                .thenReturn(Flux.error(new RuntimeException("SOAP failure")));

        StepVerifier.create(useCase.uploadDocument(history, 1L))
                .expectNextMatches(resp -> !resp.isSuccess() 
                        && "FAILURE".equals(resp.getStatus())
                        && "UNKNOWN_ERROR".equals(resp.getSyncStatus())
                        && "SOAP failure".equals(resp.getMessage())
                        && "test.pdf".equals(resp.getFilename()))
                .expectComplete()
                .verify();
    }

    @Test
    void uploadDocument_whenHomologationRepositoryThrowsException_returnsFailureResponse() {
        DocumentHistoryDTO history = DocumentHistoryDTO.builder()
                .filename("test.pdf")
                .build();

        when(homologationRepository.resolve(any()))
                .thenReturn(Mono.error(new RuntimeException("Homologation failure")));

        StepVerifier.create(useCase.uploadDocument(history, 1L))
                .expectNextMatches(resp -> !resp.isSuccess() 
                        && "FAILURE".equals(resp.getStatus())
                        && "UNKNOWN_ERROR".equals(resp.getSyncStatus())
                        && "Homologation failure".equals(resp.getMessage())
                        && "test.pdf".equals(resp.getFilename()))
                .expectComplete()
                .verify();
    }
}
