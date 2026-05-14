package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.entity.ProductHistory;
import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
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
    private DocumentRepository documentRepository;

    @Mock
    private ProductMasterRepository productMasterRepository;

    @Mock
    private ProductRestGateway productRestGateway;

    private SyncDocumentsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SyncDocumentsUseCase(documentRepository, productMasterRepository, productRestGateway);
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
            .useCase(useCase)
            .state(ProductState.PENDING)
            .isZip(false)
            .createdAt(java.time.LocalDateTime.now())
            .build();
    }

    @Test
    void execute_whenNoProducts_returnsCompletionMessage() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        verify(documentRepository, never()).save(any());
    }

    @Test
    void execute_whenProductsExist_processesEachDocument() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)));
        when(documentRepository.existsByProductIdAndDocumentId(any(), any())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(docCaptor.capture());
        Document savedDoc = docCaptor.getValue();
        assertEquals("doc1", savedDoc.getDocumentId());
        assertEquals("p1", savedDoc.getProductId());
        assertEquals(ProductState.PENDING, savedDoc.getState());
        assertEquals("retention", savedDoc.getUseCase());
    }

    @Test
    void execute_withZipDocument_parentZipNameIsNull() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", true)));
        when(documentRepository.existsByProductIdAndDocumentId(any(), any())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(docCaptor.capture());
        Document saved = docCaptor.getValue();
        assertTrue(saved.isZip());
    }

    @Test
    void execute_withMultipleProducts_processesAll() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1"), product("p2")));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)))
            .thenReturn(Flux.just(doc("p2", "doc2", false)));
        when(documentRepository.existsByProductIdAndDocumentId(any(), any())).thenReturn(Mono.just(false));
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
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.error(new RuntimeException("DB error")));

        StepVerifier.create(useCase.execute("retention"))
            .expectErrorMatches(error -> error instanceof RuntimeException
                && "DB error".equals(error.getMessage()))
            .verify();
    }

    @Test
    void execute_whenGatewayFails_ignoresErrorAndContinues() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.error(new RuntimeException("Gateway error")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        verify(documentRepository, never()).save(any());
    }

    @Test
    void execute_usesUseCaseFromParameter() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)));
        when(documentRepository.existsByProductIdAndDocumentId(any(), any())).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "extract")));

        StepVerifier.create(useCase.execute("extract"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertEquals("extract", captor.getValue().getUseCase());
    }

    @Test
    void execute_whenDocumentAlreadyExists_savesAsDuplicated() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(doc("p1", "doc1", false)));
        when(documentRepository.existsByProductIdAndDocumentId("p1", "doc1")).thenReturn(Mono.just(true));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(docCaptor.capture());
        Document savedDoc = docCaptor.getValue();
        assertEquals("doc1", savedDoc.getDocumentId());
        assertEquals("p1", savedDoc.getProductId());
        assertEquals(ProductState.ERR_DUPLICATED_DOC, savedDoc.getState());
        assertNotNull(savedDoc.getErrorMessage());
        assertTrue(savedDoc.getErrorMessage().contains("duplicado"));
        assertEquals("retention", savedDoc.getUseCase());
    }

    @Test
    void execute_mixedNewAndDuplicateDocuments_savesCorrectly() {
        when(productMasterRepository.getAllProducts()).thenReturn(Flux.just(product("p1")));
        when(productRestGateway.getDocumentsByProduct(any()))
            .thenReturn(Flux.just(
                doc("p1", "doc1", false),
                doc("p1", "doc2", false)
            ));
        when(documentRepository.existsByProductIdAndDocumentId("p1", "doc1")).thenReturn(Mono.just(true));
        when(documentRepository.existsByProductIdAndDocumentId("p1", "doc2")).thenReturn(Mono.just(false));
        when(documentRepository.save(any(Document.class)))
            .thenReturn(Mono.just(savedDocument(10L, "doc1", "retention")))
            .thenReturn(Mono.just(savedDocument(11L, "doc2", "retention")));

        StepVerifier.create(useCase.execute("retention"))
            .assertNext(result -> assertEquals("Document sync completed", result))
            .verifyComplete();

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository, times(2)).save(docCaptor.capture());

        java.util.List<Document> savedDocs = docCaptor.getAllValues();
        assertEquals(2, savedDocs.size());
        assertEquals(ProductState.ERR_DUPLICATED_DOC, savedDocs.get(0).getState());
        assertEquals("doc1", savedDocs.get(0).getDocumentId());
        assertEquals(ProductState.PENDING, savedDocs.get(1).getState());
        assertEquals("doc2", savedDocs.get(1).getDocumentId());
    }
}
