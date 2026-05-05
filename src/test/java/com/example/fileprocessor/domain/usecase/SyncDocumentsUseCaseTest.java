package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductHistory;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
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
    private DocumentHistoryRepository historyRepository;

    @Mock
    private ProductRestGateway productRestGateway;

    private SyncDocumentsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SyncDocumentsUseCase(productRepository, historyRepository, productRestGateway);
        lenient().when(historyRepository.save(any())).thenReturn(Mono.empty());
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

    @Test
    void execute_whenNoProducts_returnsCompletionMessage() {
        when(productRepository.findAll()).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        verify(historyRepository, never()).save(any());
    }

    @Test
    void execute_whenProductsExist_processesEachDocument() {
        when(productRepository.findAll()).thenReturn(Flux.just(product("p1")));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        ArgumentCaptor<DocumentHistory> captor = ArgumentCaptor.forClass(DocumentHistory.class);
        verify(historyRepository).save(captor.capture());
        DocumentHistory saved = captor.getValue();
        assertEquals("doc1", saved.documentId());
        assertEquals("p1", saved.productId());
        assertEquals(ProductState.PENDING, saved.state());
        assertEquals("retention", saved.useCase());
        assertEquals(0, saved.retry());
        assertFalse(saved.isZip());
        assertNull(saved.parentZipName());
    }

    @Test
    void execute_withZipDocument_setsParentZipName() {
        when(productRepository.findAll()).thenReturn(Flux.just(product("p1")));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", true)));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        ArgumentCaptor<DocumentHistory> captor = ArgumentCaptor.forClass(DocumentHistory.class);
        verify(historyRepository).save(captor.capture());
        DocumentHistory saved = captor.getValue();
        assertTrue(saved.isZip());
        assertEquals("bundle.zip", saved.parentZipName());
    }

    @Test
    void execute_withMultipleProducts_processesAll() {
        when(productRepository.findAll()).thenReturn(Flux.just(product("p1"), product("p2")));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)))
            .thenReturn(Flux.just(doc("p2", "doc2", false)));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        verify(historyRepository, times(2)).save(any());
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
    void execute_whenGatewayFails_propagatesError() {
        when(productRepository.findAll()).thenReturn(Flux.just(product("p1")));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.error(new RuntimeException("Gateway error")));

        StepVerifier.create(useCase.execute("retention"))
            .expectErrorMatches(error -> error instanceof RuntimeException
                && "Gateway error".equals(error.getMessage()))
            .verify();
    }

    @Test
    void execute_usesUseCaseFromParameter() {
        when(productRepository.findAll()).thenReturn(Flux.just(product("p1")));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)));

        StepVerifier.create(useCase.execute("extract"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        ArgumentCaptor<DocumentHistory> captor = ArgumentCaptor.forClass(DocumentHistory.class);
        verify(historyRepository).save(captor.capture());
        assertEquals("extract", captor.getValue().useCase());
    }
}
