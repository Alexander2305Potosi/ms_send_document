package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.animal.AnimalMaestro;
import com.example.fileprocessor.domain.entity.animal.AnimalDocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.homologation.HomologationResult;
import com.example.fileprocessor.domain.port.out.AnimalRepository;
import com.example.fileprocessor.domain.port.out.AnimalRestGateway;
import com.example.fileprocessor.domain.port.out.PersistenceGateway;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.AnimalSoapGateway;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AnimalDocumentProcessingUseCaseTest {

    @Mock
    private PersistenceGateway<Document, AnimalDocumentHistoryDTO> persistencePort;
    @Mock
    private ProductRestGateway productRestGateway;
    @Mock
    private AnimalSoapGateway soapGateway;
    @Mock
    private RulesBussinesGateway<AnimalDocumentHistoryDTO> documentValidator;
    @Mock
    private HomologationRepository homologationRepository;
    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private AnimalRestGateway animalRestGateway;

    private AnimalDocumentProcessingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AnimalDocumentProcessingUseCase(
                persistencePort, 
                productRestGateway, 
                documentValidator, 
                "/tmp/test-zip-dir",
                animalRepository,
                animalRestGateway,
                soapGateway,
                homologationRepository);
    }

    @Test
    void executeAnimalProcessingSuccess() {
        AnimalMaestro animal = AnimalMaestro.builder().id(100L).name("Cow").build();
        Document doc = Document.builder()
                .id(1L)
                .documentId("doc-1")
                .productId("prod-1")
                .name("test.pdf")
                .retryCount(0)
                .isZip(false)
                .build();

        com.example.fileprocessor.domain.entity.product.maestro.ProductDocumentFile file = 
            com.example.fileprocessor.domain.entity.product.maestro.ProductDocumentFile.builder()
                .productId("prod-1")
                .documentId("doc-1")
                .filename("test.pdf")
                .content(new byte[]{1})
                .originFolder("origin")
                .originCountry("AR")
                .isZip(false)
                .build();

        when(animalRepository.findAllAnimals()).thenReturn(Flux.just(animal));
        when(animalRestGateway.getPendingDocumentsForAnimal(100L)).thenReturn(Flux.just(doc));
        
        when(persistencePort.lockDocumentForProcessing(any(Document.class), anyInt())).thenReturn(Mono.just(1L));
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(file));
        when(documentValidator.validate(any(AnimalDocumentHistoryDTO.class), anyBoolean()))
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

        StepVerifier.create(useCase.executeAnimalProcessing())
            .expectNextMatches(FileUploadResponse::isSuccess)
            .expectComplete()
            .verify(Duration.ofSeconds(10));
            
        verify(animalRestGateway, times(1)).getPendingDocumentsForAnimal(100L);
    }
}
