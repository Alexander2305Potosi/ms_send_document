package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.valueobject.FolderExclusionRegexConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3DocumentProcessingUseCaseTest {

    @Mock
    private ProductDocumentRepository documentRepository;
    @Mock
    private S3Gateway s3Gateway;
    @Mock
    private ProductRestGateway productRestGateway;

    private S3DocumentProcessingUseCase useCase;
    private FolderExclusionRegexConfig folderExclusionRegex;

    @BeforeEach
    void setUp() {
        FileValidator fileValidator = new FileValidator(10.0, "pdf,txt,csv");
        FolderInfoExtractor folderInfoExtractor = new FolderInfoExtractor(List.of("test", "mock"));
        folderExclusionRegex = new FolderExclusionRegexConfig(List.of("excluded", "skip-me"));
        useCase = new S3DocumentProcessingUseCase(
            documentRepository, s3Gateway,
            fileValidator, folderExclusionRegex, folderInfoExtractor,
            productRestGateway
        );
    }

    @Test
    void implementationName_shouldReturnS3() {
        assertThat(useCase.implementationName()).isEqualTo("S3");
    }

    @Test
    void applyRulesMetadata_shouldRejectInvalidExtension() {
        ProductDocumentToProcess doc = ProductDocumentToProcess.builder()
            .documentId("doc1")
            .productId("prod1")
            .filename("document.exe")
            .content(new byte[]{1, 2, 3})
            .contentType("application/octet-stream")
            .status(DocumentStatus.PENDING.name())
            .fileSizeMb(0.5)
            .build();

        StepVerifier.create(useCase.applyRulesMetadata(doc))
            .expectErrorMatches(e -> e instanceof com.example.fileprocessor.domain.exception.FileValidationException)
            .verify();

        verify(documentRepository, never()).updateStatus(any(), any(), any(), any());
    }

    @Test
    void applyRulesMetadata_shouldAcceptValidExtension() {
        ProductDocumentToProcess doc = ProductDocumentToProcess.builder()
            .documentId("doc1")
            .productId("prod1")
            .filename("document.pdf")
            .content(new byte[]{1, 2, 3})
            .contentType("application/pdf")
            .status(DocumentStatus.PENDING.name())
            .fileSizeMb(0.5)
            .build();

        StepVerifier.create(useCase.applyRulesMetadata(doc))
            .assertNext(result -> {
                assertThat(result.documentId()).isEqualTo("doc1");
                assertThat(result.skipped()).isFalse();
            })
            .verifyComplete();
    }

    @Test
    void applyRulesMetadata_shouldSkipExcludedFolder() {
        ProductDocumentToProcess doc = ProductDocumentToProcess.builder()
            .documentId("doc1")
            .productId("prod1")
            .filename("document.pdf")
            .content(new byte[]{1, 2, 3})
            .contentType("application/pdf")
            .origin("s3://bucket/excluded/folder/file.pdf")
            .status(DocumentStatus.PENDING.name())
            .fileSizeMb(0.5)
            .build();

        when(documentRepository.updateStatus(any(), any(), any(), any()))
            .thenReturn(Mono.empty());

        StepVerifier.create(useCase.applyRulesMetadata(doc))
            .assertNext(result -> {
                assertThat(result.skipped()).isTrue();
                assertThat(result.documentId()).isEqualTo("doc1");
            })
            .verifyComplete();

        verify(documentRepository).updateStatus(eq("doc1"), eq(DocumentStatus.SKIPPED.name()), isNull(), eq("SKIPPED_FOLDER"));
    }

    @Test
    void applyRulesMetadata_shouldNotSkipNonExcludedFolder() {
        ProductDocumentToProcess doc = ProductDocumentToProcess.builder()
            .documentId("doc1")
            .productId("prod1")
            .filename("document.pdf")
            .content(new byte[]{1, 2, 3})
            .contentType("application/pdf")
            .origin("s3://bucket/included/folder/file.pdf")
            .status(DocumentStatus.PENDING.name())
            .fileSizeMb(0.5)
            .build();

        StepVerifier.create(useCase.applyRulesMetadata(doc))
            .assertNext(result -> {
                assertThat(result.skipped()).isFalse();
            })
            .verifyComplete();

        verify(documentRepository, never()).updateStatus(any(), any(), any(), any());
    }

    @Test
    void uploadDocument_shouldReturnSuccessWhenS3Succeeds() {
        FileUploadResult successResult = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .correlationId("corr-123")
            .processedAt(Instant.now())
            .success(true)
            .message("OK")
            .build();

        when(s3Gateway.send(any(), any(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Mono.just(successResult));

        DocumentToUpload docToUpload = new DocumentToUpload(
            ProductDocumentToProcess.builder()
                .documentId("doc1")
                .filename("doc.pdf")
                .content(new byte[]{1, 2, 3})
                .contentType("application/pdf")
                .origin("s3://bucket/parent/child/file.pdf")
                .fileSizeMb(0.5)
                .build(),
            new FolderInfo("parent", "child"),
            3L,
            false
        );

        StepVerifier.create(useCase.uploadDocument(docToUpload))
            .assertNext(result -> {
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getCorrelationId()).isEqualTo("corr-123");
            })
            .verifyComplete();
    }

    @Test
    void uploadDocument_shouldReturnFailureWhenS3Fails() {
        when(s3Gateway.send(any(), any(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyString()))
            .thenReturn(Mono.error(new ProcessingException("S3 error", "S3_ERROR", "trace-1")));

        DocumentToUpload docToUpload = new DocumentToUpload(
            ProductDocumentToProcess.builder()
                .documentId("doc1")
                .filename("doc.pdf")
                .content(new byte[]{1, 2, 3})
                .contentType("application/pdf")
                .origin("s3://bucket/parent/child/file.pdf")
                .fileSizeMb(0.5)
                .build(),
            new FolderInfo("parent", "child"),
            3L,
            false
        );

        StepVerifier.create(useCase.uploadDocument(docToUpload))
            .assertNext(result -> {
                assertThat(result.isSuccess()).isFalse();
                assertThat(result.getErrorCode()).isEqualTo("S3_ERROR");
            })
            .verifyComplete();
    }

    @Test
    void uploadDocument_shouldReturnSkippedResultWhenDocumentIsSkipped() {
        DocumentToUpload skippedDoc = new DocumentToUpload(
            ProductDocumentToProcess.builder()
                .documentId("doc1")
                .filename("doc.pdf")
                .content(null)
                .contentType("application/pdf")
                .origin("s3://bucket/excluded/file.pdf")
                .fileSizeMb(0)
                .build(),
            new FolderInfo("excluded", "folder"),
            0L,
            true
        );

        StepVerifier.create(useCase.uploadDocument(skippedDoc))
            .assertNext(result -> {
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getStatus()).isEqualTo(DocumentStatus.SKIPPED.name());
                assertThat(result.getMessage()).contains("folder exclusion");
            })
            .verifyComplete();

        verify(s3Gateway, never()).send(any(), any(), anyString(), anyString(), anyLong(), anyString(), anyString(), anyString());
    }
}
