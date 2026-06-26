package com.example.fileprocessor.domain.usecase;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.ERR_DUPLICATED_DOC;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILED;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.NO_SUCURSAL;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PENDING;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.maestro.ProductMaestro;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.domain.port.out.ProductLocalRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncDocumentsUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ProductMasterRepository productMasterRepository;

    @Mock
    private ProductRestGateway productRestGateway;

    @Mock
    private ProductLocalRepository productLocalRepository;

    private SyncDocumentsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SyncDocumentsUseCase(documentRepository, productMasterRepository, productRestGateway, productLocalRepository);
    }

    private static ProductMaestro product(String id) {
        return ProductMaestro.builder().productId(id).name("Product-" + id).build();
    }

    private static Document doc(String productId, String docId, boolean isZip) {
        return Document.builder()
            .productId(productId)
            .documentId(docId)
            .name(isZip ? "bundle.zip" : "file.pdf")
            .isZip(isZip)
            .build();
    }

    private static Document savedDocument(Long id, String docId, String useCase) {
        return Document.builder()
            .id(id)
            .documentId(docId)
            .productId("p1")
            .name("file.pdf")
            .useCase(useCase)
            .state(PENDING.name())
            .isZip(false)
            .createdAt(java.time.LocalDateTime.now())
            .build();
    }

    @Test
    void executeWhenNoProductsReturnsCompletionMessage() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        verify(documentRepository, never()).save(any());
    }

    @Test
    void executeWhenProductsExistProcessesEachDocument() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productLocalRepository.findBranchByProductId("p1")).thenReturn(Mono.just("Sucursal Bogota"));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)));
        when(documentRepository.existsByProductIdAndDocumentId(any(), any())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(docCaptor.capture());
        Document savedDoc = docCaptor.getValue();
        assertEquals("doc1", savedDoc.getDocumentId());
        assertEquals("p1", savedDoc.getProductId());
        assertEquals("Sucursal Bogota", savedDoc.getSucursal());
        assertEquals(PENDING.name(), savedDoc.getState());
        assertEquals("retention", savedDoc.getUseCase());
    }

    @Test
    void executeWithZipDocumentParentZipNameIsNull() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productLocalRepository.findBranchByProductId("p1")).thenReturn(Mono.just("Sucursal Medellin"));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", true)));
        when(documentRepository.existsByProductIdAndDocumentId(any(), any())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(docCaptor.capture());
        Document saved = docCaptor.getValue();
        assertTrue(Boolean.TRUE.equals(saved.getIsZip()));
        assertEquals("Sucursal Medellin", saved.getSucursal());
    }

    @Test
    void executeWithMultipleProductsProcessesAll() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1"), product("p2")));
        when(productLocalRepository.findBranchByProductId(anyString())).thenReturn(Mono.just("Sucursal Multi"));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)))
            .thenReturn(Flux.just(doc("p2", "doc2", false)));
        when(documentRepository.existsByProductIdAndDocumentId(any(), any())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "retention")))
            .thenReturn(Mono.just(savedDocument(11L, "doc2", "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        verify(documentRepository, times(2)).save(any());
    }

    @Test
    void executeWhenRepositoryFailsPropagatesError() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.error(new RuntimeException("DB error")));

        StepVerifier.create(useCase.execute("retention"))
            .expectErrorMatches(error -> error instanceof RuntimeException
                && "DB error".equals(error.getMessage()))
            .verify(Duration.ofSeconds(10));
    }

    @Test
    void executeWhenGatewayFailsIgnoresErrorAndContinues() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productLocalRepository.findBranchByProductId("p1")).thenReturn(Mono.just("Sucursal FailTest"));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.error(new RuntimeException("Gateway error")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        verify(documentRepository, never()).save(any());
    }

    @Test
    void executeUsesUseCaseFromParameter() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productLocalRepository.findBranchByProductId("p1")).thenReturn(Mono.just("Sucursal UC"));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)));
        when(documentRepository.existsByProductIdAndDocumentId(any(), any())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "extract")));

        StepVerifier.create(useCase.execute("extract"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertEquals("extract", captor.getValue().getUseCase());
        assertEquals("Sucursal UC", captor.getValue().getSucursal());
    }

    @Test
    void executeWhenDocumentAlreadyExistsSavesAsDuplicated() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productLocalRepository.findBranchByProductId("p1")).thenReturn(Mono.just("Sucursal Dup"));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)));
        when(documentRepository.existsByProductIdAndDocumentId("p1", "doc1")).thenReturn(Mono.just(true));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(docCaptor.capture());
        Document savedDoc = docCaptor.getValue();
        assertEquals("doc1", savedDoc.getDocumentId());
        assertEquals("p1", savedDoc.getProductId());
        assertEquals("Sucursal Dup", savedDoc.getSucursal());
        assertEquals(ERR_DUPLICATED_DOC.name(), savedDoc.getState());
        assertNotNull(savedDoc.getSyncMessage());
        assertEquals(ERR_DUPLICATED_DOC.value(), savedDoc.getSyncMessage());
        assertEquals("retention", savedDoc.getUseCase());
    }

    @Test
    void executeWhenBranchNotFoundSavesPlaceholderAndSkipsDownload() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productLocalRepository.findBranchByProductId("p1")).thenReturn(Mono.empty());
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, NO_SUCURSAL.name(), "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        verify(productRestGateway, never()).getDocumentsByProduct(any());

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(docCaptor.capture());
        Document savedDoc = docCaptor.getValue();
        assertEquals(NO_SUCURSAL.name(), savedDoc.getDocumentId());
        assertEquals("p1", savedDoc.getProductId());
        assertEquals(FAILED.name(), savedDoc.getState());
        assertEquals(NO_SUCURSAL.value(), savedDoc.getSyncMessage());
        assertEquals("retention", savedDoc.getUseCase());
    }

    @Test
    void executeWhenLastProcessedProductExistsResumesFromNextProduct() {
        // Given
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p2")));
        when(productLocalRepository.findBranchByProductId("p2")).thenReturn(Mono.just("Sucursal Medellin"));
        when(productRestGateway.getDocumentsByProduct(any())).thenReturn(Flux.just(doc("p2", "doc2", false)));
        when(documentRepository.existsByProductIdAndDocumentId(any(), any())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class))).thenReturn(Mono.just(savedDocument(12L, "doc2", "retention")));

        // When & Then
        StepVerifier.create(useCase.execute("retention").contextWrite(reactor.util.context.Context.of("last_product_id", "p1")))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        verify(productMasterRepository).getAllProducts();
    }
}
