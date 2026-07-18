package com.example.fileprocessor.domain.usecase;

import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.BUSINESS_REJECTION;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.EMPTY_CONTENT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILED;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.GATEWAY_TIMEOUT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PATTERN_MISMATCH;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PENDING;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PROCESSED;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.product.ProcessingContext;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.product.maestro.ProductDocumentFile;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbstractDocumentProcessingUseCaseTest {

    @Mock
    private DocumentPersistenceGateway persistencePort;
    @Mock
    private ProductRestGateway productRestGateway;
    @Mock
    private RulesBussinesGateway<DocumentHistoryDTO> documentValidator;

    private AbstractDocumentProcessingUseCase<Document, DocumentHistoryDTO> useCase;

    interface UploadFunc {
        Flux<FileUploadResponse> apply(DocumentHistoryDTO h, Long id);
    }

    private AbstractDocumentProcessingUseCase<Document, DocumentHistoryDTO> createUseCase(UploadFunc uploadFunc) {
        return new AbstractDocumentProcessingUseCase<Document, DocumentHistoryDTO>(
            persistencePort, documentValidator, "/tmp/test-zip-dir"
        ) {
            @Override
            protected Flux<Document> getPendingDocuments(java.time.LocalDateTime startOfDay) {
                return persistencePort.findPendingDocumentsToday(implementationName(), startOfDay);
            }

            @Override
            protected DocumentHistoryDTO buildInitialHistory(Document doc) {
                return DocumentHistoryDTO.fromDocument(doc);
            }

            @Override
            protected Mono<ProcessingContext<DocumentHistoryDTO>> downloadDocumentContent(DocumentHistoryDTO baseHistory) {
                return productRestGateway.getDocument(baseHistory.getProductId(), baseHistory.getBusinessDocumentId())
                        .map(file -> new ProcessingContext<>(baseHistory, file.getContent()));
            }

            @Override
            protected Flux<FileUploadResponse> uploadDocument(ProcessingContext<DocumentHistoryDTO> context, Long docId) {
                return uploadFunc.apply(context.getHistory(), docId);
            }

            @Override
            protected DocumentHistoryDTO buildDecompressedEntryHistory(DocumentHistoryDTO zipHistory, String entryName) {
                return zipHistory.toBuilder()
                        .businessDocumentId(zipHistory.getBusinessDocumentId() + "/" + entryName)
                        .filename(entryName)
                        .isZip(false)
                        .build();
            }

            @Override
            protected String implementationName() {
                return "TEST";
            }
        };
    }

    @BeforeEach
    void setUp() {
        useCase = createUseCase((h, id) -> Flux.just(FileUploadResponse.builder().success(true).build()));

        lenient().when(persistencePort.lockDocumentForProcessing(any(Document.class), anyInt()))
            .thenReturn(Mono.just(1L));

        lenient().when(persistencePort.finalizeProcessingAtomically(any()))
            .thenReturn(Mono.empty());
            
        lenient().when(persistencePort.saveHistory(any()))
            .thenReturn(Mono.empty());

        lenient().when(documentValidator.validate(any()))
            .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void executePendingDocumentsWithTechnicalErrorIncrementsRetryCount() {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-1")
            .productId("prod-1")
            .name("test.pdf")
            .state(PENDING.name())
            .retryCount(0)
            .isZip(false)
            .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any()))
            .thenReturn(Flux.just(doc));
        
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.error(new ProcessingException("Timeout error", GATEWAY_TIMEOUT.name(), (Throwable) null)));

        StepVerifier.create(useCase.executePendingDocuments()
                .contextWrite(ctx -> ctx.put("message-id", "test-trace-tech-error")))
            .assertNext(result -> {
                assertEquals(GATEWAY_TIMEOUT.name(), result.getSyncStatus());
            })
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        verify(persistencePort).finalizeProcessingAtomically(
            argThat(h -> PENDING.name().equals(h.getState()) && h.getRetryCount() == 0 
                    && h.getSyncStatus().equals(GATEWAY_TIMEOUT.name()))
        );
    }

    @Test
    void executePendingDocumentsWithMaxRetriesReachedMarksAsFailed() {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-1")
            .productId("prod-1")
            .name("test.pdf")
            .state(PENDING.name())
            .retryCount(3) 
            .isZip(false)
            .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any()))
            .thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.error(new ProcessingException("Final timeout", GATEWAY_TIMEOUT.name(), (Throwable) null)));

        StepVerifier.create(useCase.executePendingDocuments()
                .contextWrite(ctx -> ctx.put("message-id", "test-trace-max-retries")))
            .expectNextCount(1)
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        verify(persistencePort).finalizeProcessingAtomically(
            argThat(h -> FAILED.name().equals(h.getState()) && h.getRetryCount() == 3)
        );
    }

    @Test
    void executePendingDocumentsWithSuccessMarksAsProcessed() {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-1")
            .productId("prod-1")
            .name("test.pdf")
            .state(PENDING.name())
            .retryCount(0)
            .isZip(false)
            .build();

        ProductDocumentFile file = ProductDocumentFile.builder()
                .productId("prod-1")
                .documentId("doc-1")
                .filename("test.pdf")
                .content(new byte[]{1, 2, 3})
                .size(3L)
                .isZip(false)
                .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any()))
            .thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.just(file));
        
        when(documentValidator.validate(any(DocumentHistoryDTO.class), anyBoolean()))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.executePendingDocuments()
                .contextWrite(ctx -> ctx.put("message-id", "test-trace-success")))
            .expectNextCount(1)
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        ArgumentCaptor<DocumentHistoryDTO> historyCaptor = ArgumentCaptor.forClass(DocumentHistoryDTO.class);
        
        verify(persistencePort).finalizeProcessingAtomically(historyCaptor.capture());
        
        assertEquals(PROCESSED.name(), historyCaptor.getValue().getState());
        assertNotNull(historyCaptor.getValue().getCompletedAt());
    }

    @Test
    void executePendingDocumentsWithMultipleDocumentsProcessesAllSequentially() {
        Document doc1 = Document.builder().id(1L).documentId("doc-1").productId("p1").state("PENDING").isZip(false).build();
        Document doc2 = Document.builder().id(2L).documentId("doc-2").productId("p2").state("PENDING").isZip(false).build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any()))
            .thenReturn(Flux.just(doc1, doc2));
        
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.just(ProductDocumentFile.builder().isZip(false).build()));
        
        when(documentValidator.validate(any(), anyBoolean()))
            .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextCount(2)
            .expectComplete()
            .verify(Duration.ofSeconds(10));

        verify(persistencePort, times(2)).finalizeProcessingAtomically(any());
    }

    @Test
    void executePendingDocumentsWithTechnicalRetrySavesAuditOnly() {
        Document doc = Document.builder().id(1L).documentId("doc-1").productId("p1").state("PENDING").isZip(false).retryCount(0).build();
        
        when(persistencePort.findPendingDocumentsToday(anyString(), any()))
            .thenReturn(Flux.just(doc));
        
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.just(ProductDocumentFile.builder().isZip(false).build()));
        
        when(documentValidator.validate(any(), anyBoolean()))
            .thenReturn(Mono.just(DocumentHistoryDTO.builder().build()));

        useCase = createUseCase((history, docId) -> Flux.just(FileUploadResponse.builder()
                .success(false)
                .technicalRetry(true)
                .attemptCount(1)
                .syncStatus(GATEWAY_TIMEOUT.name())
                .message("Technical error")
                .build()));

        StepVerifier.create(useCase.executePendingDocuments())
            .expectNextCount(1)
            .expectComplete()
            .verify(Duration.ofSeconds(5));

        verify(persistencePort).saveHistory(any());
        verify(persistencePort).finalizeProcessingAtomically(any());
    }

    @Test
    void executePendingDocumentsWithValidationErrorMarksAsFailed() {
        Document doc = Document.builder().id(1L).documentId("doc-1").productId("p1").state("PENDING").isZip(false).build();
        
        when(persistencePort.findPendingDocumentsToday(anyString(), any()))
            .thenReturn(Flux.just(doc));
        
        when(productRestGateway.getDocument(anyString(), anyString()))
            .thenReturn(Mono.just(ProductDocumentFile.builder().isZip(false).build()));
        
        when(documentValidator.validate(any(), anyBoolean()))
            .thenReturn(Mono.error(new ProcessingException("Rules failed", PATTERN_MISMATCH.name())));

        StepVerifier.create(useCase.executePendingDocuments())
            .assertNext(resp -> {
                assertFalse(resp.isSuccess());
                assertEquals(PATTERN_MISMATCH.name(), resp.getSyncStatus());
            })
            .expectComplete()
            .verify(Duration.ofSeconds(5));

        verify(persistencePort).finalizeProcessingAtomically(argThat(h -> 
            BUSINESS_REJECTION.name().equals(h.getState()) &&
            PATTERN_MISMATCH.name().equals(h.getSyncStatus())
        ));
    }

    @Test
    void executePendingDocumentsWithZipValidationErrorSetsFilenameInException() throws Exception {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-zip")
            .productId("p1")
            .state("PENDING")
            .isZip(true)
            .name("archive.zip")
            .build();

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("inner-file.pdf");
            zos.putNextEntry(entry);
            zos.write("dummy-data".getBytes());
            zos.closeEntry();
        }
        byte[] zipBytes = baos.toByteArray();

        ProductDocumentFile file = ProductDocumentFile.builder()
                .productId("p1")
                .documentId("doc-zip")
                .filename("archive.zip")
                .content(zipBytes)
                .isZip(true)
                .build();

        when(persistencePort.findPendingDocumentsToday(anyString(), any())).thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(file));
        
        when(documentValidator.validate(any(), anyBoolean()))
            .thenReturn(Mono.error(new ProcessingException("Invalid inner file", PATTERN_MISMATCH.name())));

        StepVerifier.create(useCase.executePendingDocuments())
            .assertNext(resp -> {
                assertFalse(resp.isSuccess());
                assertEquals("inner-file.pdf", resp.getFilename());
                assertEquals(PATTERN_MISMATCH.name(), resp.getSyncStatus());
            })
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    void executePendingDocumentsWithZipValidationErrorNotBusinessRuleSetsFilenameInException() throws Exception {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-zip-non-br")
            .productId("p1")
            .state("PENDING")
            .isZip(true)
            .name("archive.zip")
            .build();

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("inner-file.pdf");
            zos.putNextEntry(entry);
            zos.write("dummy-data".getBytes());
            zos.closeEntry();
        }
        byte[] zipBytes = baos.toByteArray();

        ProductDocumentFile file = ProductDocumentFile.builder()
                .productId("p1")
                .documentId("doc-zip-non-br")
                .filename("archive.zip")
                .content(zipBytes)
                .isZip(true)
                .build();

        when(persistencePort.findPendingDocumentsToday(anyString(), any())).thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(file));
        
        when(documentValidator.validate(any(), anyBoolean()))
            .thenReturn(Mono.error(new ProcessingException("Invalid inner file", "SOME_NON_BUSINESS_RULE_ERROR")));

        StepVerifier.create(useCase.executePendingDocuments())
            .assertNext(resp -> {
                assertFalse(resp.isSuccess());
                assertEquals("inner-file.pdf", resp.getFilename());
                assertEquals("SOME_NON_BUSINESS_RULE_ERROR", resp.getSyncStatus());
                assertTrue(resp.getMessage().contains("Invalid inner file"));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    void executePendingDocumentsWithZipEmptyThrowsEmptyContent() throws Exception {
        Document doc = Document.builder()
            .id(1L)
            .documentId("doc-zip-empty")
            .productId("p1")
            .state("PENDING")
            .isZip(true)
            .name("archive.zip")
            .build();

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
        }
        byte[] zipBytes = baos.toByteArray();

        ProductDocumentFile file = ProductDocumentFile.builder()
                .productId("p1")
                .documentId("doc-zip-empty")
                .filename("archive-empty.zip")
                .content(zipBytes)
                .isZip(true)
                .build();

        when(persistencePort.findPendingDocumentsToday(anyString(), any())).thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(file));

        StepVerifier.create(useCase.executePendingDocuments())
            .assertNext(resp -> {
                assertFalse(resp.isSuccess());
                assertEquals(EMPTY_CONTENT.name(), resp.getSyncStatus());
            })
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    void executePendingDocumentsWithZipMultipleFilesAllSuccessMarksAsProcessed() throws Exception {
        byte[] zipBytes = createZipBytes("file1.pdf", "file2.pdf");

        Document doc = Document.builder()
                .id(1L)
                .documentId("doc-zip-multi")
                .productId("p1")
                .state(PENDING.name())
                .isZip(true)
                .name("archive.zip")
                .retryCount(0)
                .build();

        ProductDocumentFile file = ProductDocumentFile.builder()
                .productId("p1")
                .documentId("doc-zip-multi")
                .filename("archive.zip")
                .content(zipBytes)
                .isZip(true)
                .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any())).thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(file));
        when(documentValidator.validate(any(), anyBoolean())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        useCase = createUseCase((h, id) -> Flux.just(FileUploadResponse.builder().success(true).syncStatus("SUCCESS").build()));

        StepVerifier.create(useCase.executePendingDocuments())
                .expectNextCount(1)
                .expectComplete()
                .verify(Duration.ofSeconds(10));

        verify(persistencePort, times(2)).saveHistory(any(DocumentHistoryDTO.class));
        verify(persistencePort).finalizeProcessingAtomically(argThat(h ->
            PROCESSED.name().equals(h.getState()) &&
            h.getFilename().equals("archive.zip")
        ));
    }

    @Test
    void executePendingDocumentsWithZipPartialFailureMarksAsBusinessRejection() throws Exception {
        byte[] zipBytes = createZipBytes("file1.pdf", "file2.pdf");

        Document doc = Document.builder()
                .id(1L)
                .documentId("doc-zip-partial")
                .productId("p1")
                .state(PENDING.name())
                .isZip(true)
                .name("archive.zip")
                .retryCount(0)
                .build();

        ProductDocumentFile file = ProductDocumentFile.builder()
                .productId("p1")
                .documentId("doc-zip-partial")
                .filename("archive.zip")
                .content(zipBytes)
                .isZip(true)
                .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any())).thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(file));
        when(documentValidator.validate(any(), anyBoolean())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        useCase = createUseCase((h, id) -> {
            if (h.getFilename().equals("file1.pdf")) {
                return Flux.just(FileUploadResponse.builder().success(true).syncStatus("SUCCESS").build());
            } else {
                return Flux.just(FileUploadResponse.builder()
                        .success(false)
                        .syncStatus(PATTERN_MISMATCH.name())
                        .build());
            }
        });

        StepVerifier.create(useCase.executePendingDocuments())
                .expectNextCount(1)
                .expectComplete()
                .verify(Duration.ofSeconds(10));

        verify(persistencePort, times(2)).saveHistory(any(DocumentHistoryDTO.class));
        verify(persistencePort).finalizeProcessingAtomically(argThat(h ->
            BUSINESS_REJECTION.name().equals(h.getState())
        ));
    }

    @Test
    void executePendingDocumentsWithZipTechnicalRetrySavesIntermediateHistory() throws Exception {
        byte[] zipBytes = createZipBytes("file1.pdf");

        Document doc = Document.builder()
                .id(1L)
                .documentId("doc-zip-tech-retry")
                .productId("p1")
                .state(PENDING.name())
                .isZip(true)
                .name("archive.zip")
                .retryCount(0)
                .build();

        ProductDocumentFile file = ProductDocumentFile.builder()
                .productId("p1")
                .documentId("doc-zip-tech-retry")
                .filename("archive.zip")
                .content(zipBytes)
                .isZip(true)
                .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any())).thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(file));
        when(documentValidator.validate(any(), anyBoolean())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        useCase = createUseCase((h, id) -> Flux.just(FileUploadResponse.builder()
                .success(false)
                .technicalRetry(true)
                .attemptCount(1)
                .syncStatus(GATEWAY_TIMEOUT.name())
                .message("Technical retry")
                .build()));

        StepVerifier.create(useCase.executePendingDocuments())
                .expectNextCount(1)
                .expectComplete()
                .verify(Duration.ofSeconds(10));

        verify(persistencePort, times(1)).saveHistory(any(DocumentHistoryDTO.class));
        verify(persistencePort, times(1)).finalizeProcessingAtomically(any());
    }

    @Test
    void executePendingDocumentsWithZipTechnicalAndFinalSuccessSavesAllHistory() throws Exception {
        byte[] zipBytes = createZipBytes("file1.pdf");

        Document doc = Document.builder()
                .id(1L)
                .documentId("doc-zip-mixed")
                .productId("p1")
                .state(PENDING.name())
                .isZip(true)
                .name("archive.zip")
                .retryCount(0)
                .build();

        ProductDocumentFile file = ProductDocumentFile.builder()
                .productId("p1")
                .documentId("doc-zip-mixed")
                .filename("archive.zip")
                .content(zipBytes)
                .isZip(true)
                .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any())).thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(file));
        when(documentValidator.validate(any(), anyBoolean())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        useCase = createUseCase((h, id) -> Flux.just(
                FileUploadResponse.builder()
                        .success(false)
                        .technicalRetry(true)
                        .attemptCount(1)
                        .syncStatus(GATEWAY_TIMEOUT.name())
                        .message("Attempt 1 failure")
                        .build(),
                FileUploadResponse.builder()
                        .success(true)
                        .technicalRetry(false)
                        .attemptCount(2)
                        .syncStatus("SUCCESS")
                        .message("Attempt 2 success")
                        .build()
        ));

        StepVerifier.create(useCase.executePendingDocuments())
                .expectNextCount(1)
                .expectComplete()
                .verify(Duration.ofSeconds(10));

        ArgumentCaptor<DocumentHistoryDTO> captor = ArgumentCaptor.forClass(DocumentHistoryDTO.class);
        verify(persistencePort, times(2)).saveHistory(captor.capture());
        DocumentHistoryDTO savedHistory = captor.getAllValues().get(1);
        assertEquals("file1.pdf", savedHistory.getFilename());
        assertEquals(PROCESSED.name(), savedHistory.getState());
        assertEquals(2, savedHistory.getRetryCount());

        verify(persistencePort, times(1)).finalizeProcessingAtomically(any());
    }

    @Test
    void executePendingDocumentsWithZipMultipleFilesMixedRetriesSavesCorrectHistory() throws Exception {
        byte[] zipBytes = createZipBytes("file1.pdf", "file2.pdf");

        Document doc = Document.builder()
                .id(1L)
                .documentId("doc-zip-multi-mixed")
                .productId("p1")
                .state(PENDING.name())
                .isZip(true)
                .name("archive.zip")
                .retryCount(0)
                .build();

        ProductDocumentFile file = ProductDocumentFile.builder()
                .productId("p1")
                .documentId("doc-zip-multi-mixed")
                .filename("archive.zip")
                .content(zipBytes)
                .isZip(true)
                .build();

        when(persistencePort.findPendingDocumentsToday(eq("TEST"), any())).thenReturn(Flux.just(doc));
        when(productRestGateway.getDocument(anyString(), anyString())).thenReturn(Mono.just(file));
        when(documentValidator.validate(any(), anyBoolean())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        useCase = createUseCase((h, id) -> {
            if (h.getFilename().equals("file1.pdf")) {
                return Flux.just(
                        FileUploadResponse.builder()
                                .success(true)
                                .technicalRetry(false)
                                .attemptCount(1)
                                .syncStatus("SUCCESS")
                                .build()
                );
            } else {
                return Flux.just(
                        FileUploadResponse.builder()
                                .success(false)
                                .technicalRetry(true)
                                .attemptCount(1)
                                .syncStatus(GATEWAY_TIMEOUT.name())
                                .message("Attempt 1 failure")
                                .build(),
                        FileUploadResponse.builder()
                                .success(true)
                                .technicalRetry(false)
                                .attemptCount(2)
                                .syncStatus("SUCCESS")
                                .message("Attempt 2 success")
                                .build()
                );
            }
        });

        StepVerifier.create(useCase.executePendingDocuments())
                .expectNextCount(1)
                .expectComplete()
                .verify(Duration.ofSeconds(10));

        ArgumentCaptor<DocumentHistoryDTO> captor = ArgumentCaptor.forClass(DocumentHistoryDTO.class);
        verify(persistencePort, times(3)).saveHistory(captor.capture());
        
        List<DocumentHistoryDTO> savedHistories = captor.getAllValues();
        assertEquals(3, savedHistories.size());

        DocumentHistoryDTO history1 = savedHistories.stream()
                .filter(h -> "file1.pdf".equals(h.getFilename()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, history1.getRetryCount());

        DocumentHistoryDTO history2 = savedHistories.stream()
                .filter(h -> "file2.pdf".equals(h.getFilename()) && h.getRetryCount() == 2)
                .findFirst()
                .orElseThrow();
        assertEquals(2, history2.getRetryCount());

        verify(persistencePort, times(1)).finalizeProcessingAtomically(any());
    }

    private byte[] createZipBytes(String... filenames) throws java.io.IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            for (String name : filenames) {
                zos.putNextEntry(new java.util.zip.ZipEntry(name));
                zos.write("dummy content".getBytes());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
