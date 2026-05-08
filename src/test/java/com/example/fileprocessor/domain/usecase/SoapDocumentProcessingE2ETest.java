package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.HomologationResult;
import com.example.fileprocessor.domain.entity.ProductDocumentFile;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class SoapDocumentProcessingE2ETest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentHistoryRepository historyRepository;

    @Autowired
    private SoapDocumentProcessingUseCase useCase;

    @MockBean
    private SoapGateway soapGateway;

    @MockBean
    private ProductRestGateway productRestGateway;

    @MockBean
    private HomologationRepository homologationRepository;

    // ── T15: Full pipeline success → SUCCESS trace in DB ──────────────

    @Test
    void e2e_processPendingDocument_successTraceInDatabase() {
        Document doc = Document.builder()
            .documentId("doc-e2e-success")
            .productId("prod-1")
            .name("test.pdf")
            .state("PENDING")
            .useCase("SOAP")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        Document saved = documentRepository.save(doc).block();
        assertNotNull(saved);
        Long docId = saved.id();

        ProductDocumentFile file = ProductDocumentFile.builder()
            .documentId("doc-e2e-success")
            .filename("test.pdf")
            .content(new byte[]{1, 2, 3})
            .contentType("application/pdf")
            .size(3L)
            .isZip(false)
            .origin("test-origin")
            .pais("AR")
            .build();

        when(productRestGateway.getDocument("prod-1", "doc-e2e-success"))
            .thenReturn(Mono.just(file));
        when(homologationRepository.resolve("test-origin", "AR"))
            .thenReturn(Mono.just(new HomologationResult("Mapped Origin", "Argentina")));

        FileUploadResult successResult = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .success(true)
            .correlationId("corr-e2e-success")
            .processedAt(Instant.now())
            .build();

        when(soapGateway.send(any(FileUploadRequest.class)))
            .thenReturn(Mono.just(successResult));

        StepVerifier.create(useCase.executePendingDocuments())
            .assertNext(result -> {
                assertTrue(result.isSuccess());
                assertEquals("corr-e2e-success", result.getCorrelationId());
            })
            .verifyComplete();

        DocumentHistory trace = historyRepository.findLastAudit(docId, "SOAP").block();
        assertNotNull(trace, "Expected a SUCCESS trace in historico_documentos");
        assertEquals(DocumentStatus.SUCCESS.name(), trace.result());
        assertEquals("test.pdf", trace.filename());
        assertEquals(Integer.valueOf(0), trace.retry());
        assertNull(trace.errorCode());
        assertEquals("SOAP", trace.operation());
    }

    // ── T16: Upload error → FAILURE trace with retry count ────────────

    @Test
    void e2e_processPendingDocument_uploadError_recordsFailureTrace() {
        Document doc = Document.builder()
            .documentId("doc-e2e-fail")
            .productId("prod-1")
            .name("test.pdf")
            .state("PENDING")
            .useCase("SOAP")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        Document saved = documentRepository.save(doc).block();
        assertNotNull(saved);
        Long docId = saved.id();

        ProductDocumentFile file = ProductDocumentFile.builder()
            .documentId("doc-e2e-fail")
            .filename("test.pdf")
            .content(new byte[]{1, 2, 3})
            .contentType("application/pdf")
            .size(3L)
            .isZip(false)
            .origin("test-origin")
            .pais("AR")
            .build();

        when(productRestGateway.getDocument("prod-1", "doc-e2e-fail"))
            .thenReturn(Mono.just(file));
        when(homologationRepository.resolve("test-origin", "AR"))
            .thenReturn(Mono.just(new HomologationResult("Mapped Origin", "Argentina")));

        when(soapGateway.send(any(FileUploadRequest.class)))
            .thenReturn(Mono.error(new RuntimeException("SOAP connection refused")));

        StepVerifier.create(useCase.executePendingDocuments())
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(DocumentStatus.FAILURE.name(), result.getStatus());
            })
            .verifyComplete();

        DocumentHistory trace = historyRepository.findLastAudit(docId, "SOAP").block();
        assertNotNull(trace, "Expected a FAILURE trace in historico_documentos");
        assertEquals(DocumentStatus.FAILURE.name(), trace.result());
        assertEquals("test.pdf", trace.filename());
        assertEquals("SOAP", trace.operation());
        assertNotNull(trace.errorMessage());
        assertEquals(ProcessingResultCodes.UNKNOWN_ERROR, trace.errorCode());
        assertNotNull(trace.stackTrace());
    }
}
