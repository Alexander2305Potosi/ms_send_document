package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductHistory;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncDocumentsUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ProductRestGateway productRestGateway;

    private SyncDocumentsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SyncDocumentsUseCase(productRepository, documentRepository, productRestGateway);
    }

    private static ProductHistory product(String id) {
        return ProductHistory.builder().productId(id).name("Product-" + id).build();
    }

    private static ProductDocumentHistory doc(String productId, String docId, boolean isZip) {
        return ProductDocumentHistory.builder()
            .productId(productId)
            .documentId(docId)
            .filename(isZip ? "bundle.zip" : "file.pdf")
            .isZip(isZip)
            .pais("AR")
            .size(1L)
            .origin("test-origin")
            .content(new byte[]{1})
            .build();
    }

    private static Document savedDocument(Long id, String docId, String useCase) {
        return Document.builder()
            .id(id)
            .documentId(docId)
            .productId("p1")
            .name("file.pdf")
            .owner("p1")
            .useCase(useCase)
            .state(ProductState.PENDING)
            .isZip(false)
            .createdAt(java.time.LocalDateTime.now())
            .updatedAt(java.time.LocalDateTime.now())
            .build();
    }

    @Test
    void execute_whenNoProducts_returnsCompletionMessage() {
        when(productRepository.findAll()).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        verify(documentRepository, never()).save(any());
    }

    @Test
    void execute_whenProductsExist_processesEachDocument() {
        when(productRepository.findAll()).thenReturn(Flux.just(product("p1")));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(docCaptor.capture());
        Document savedDoc = docCaptor.getValue();
        assertEquals("doc1", savedDoc.documentId());
        assertEquals("p1", savedDoc.productId());
        assertEquals(ProductState.PENDING, savedDoc.state());
        assertEquals("retention", savedDoc.useCase());
    }

    @Test
    void execute_withZipDocument_parentZipNameIsNull() {
        when(productRepository.findAll()).thenReturn(Flux.just(product("p1")));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", true)));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(docCaptor.capture());
        Document saved = docCaptor.getValue();
        assertTrue(saved.isZip());
        assertNull(saved.parentZipName());
    }

    @Test
    void execute_withMultipleProducts_processesAll() {
        when(productRepository.findAll()).thenReturn(Flux.just(product("p1"), product("p2")));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)))
            .thenReturn(Flux.just(doc("p2", "doc2", false)));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "retention")))
            .thenReturn(Mono.just(savedDocument(11L, "doc2", "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        verify(documentRepository, times(2)).save(any());
    }

    @Test
    void execute_whenRepositoryFails_propagatesError() {
        when(productRepository.findAll()).thenReturn(Flux.error(new RuntimeException("DB error")));

        StepVerifier.create(useCase.execute("retention"))
            .expectErrorMatches(error -> error instanceof RuntimeException
                && "DB error".equals(error.getMessage()))
            .verify();
    }

    @Test
    void execute_whenGatewayFails_ignoresErrorAndContinues() {
        when(productRepository.findAll()).thenReturn(Flux.just(product("p1")));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.error(new RuntimeException("Gateway error")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        verify(documentRepository, never()).save(any());
    }

    @Test
    void execute_usesUseCaseFromParameter() {
        when(productRepository.findAll()).thenReturn(Flux.just(product("p1")));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "extract")));

        StepVerifier.create(useCase.execute("extract"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertEquals("extract", captor.getValue().useCase());
    }
}