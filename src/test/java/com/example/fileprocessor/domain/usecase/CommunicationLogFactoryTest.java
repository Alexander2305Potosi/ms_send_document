package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CommunicationLogFactoryTest {

    private CommunicationLogFactory factory;

    @BeforeEach
    void setUp() {
        factory = new CommunicationLogFactory("SOAP");
    }

    @Test
    void create_shouldBuildLogWithAllFields() {
        DocumentSendRequest request = DocumentSendRequest.builder()
            .documentId("doc-123")
            .filename("test.pdf")
            .traceId("trace-456")
            .fileContent(new byte[]{1, 2, 3})
            .build();

        FileUploadResult result = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .correlationId("corr-789")
            .traceId("trace-456")
            .processedAt(Instant.now())
            .success(true)
            .build();

        Instant startTime = Instant.now().minusMillis(100);

        var log = factory.create(request, result, 2, startTime, Map.of("key", "value"));

        assertThat(log.getTraceId()).isEqualTo("trace-456");
        assertThat(log.getDocumentId()).isEqualTo("doc-123");
        assertThat(log.getStatus()).isEqualTo(DocumentStatus.SUCCESS.name());
        assertThat(log.getRetryCount()).isEqualTo(2);
        assertThat(log.getFilename()).isEqualTo("test.pdf");
        assertThat(log.getGatewayName()).isEqualTo("SOAP");
        assertThat(log.getLatencyMs()).isGreaterThanOrEqualTo(100);
        assertThat(log.getMetadata()).contains("key", "value");
    }

    @Test
    void create_shouldHandleNullMetadata() {
        DocumentSendRequest request = DocumentSendRequest.builder()
            .documentId("doc-123")
            .filename("test.pdf")
            .traceId("trace-456")
            .build();

        FileUploadResult result = FileUploadResult.builder()
            .status(DocumentStatus.SUCCESS.name())
            .traceId("trace-456")
            .processedAt(Instant.now())
            .success(true)
            .build();

        var log = factory.create(request, result, 0, Instant.now(), null);

        assertThat(log.getMetadata()).isEqualTo("{}");
    }

    @Test
    void createForSkipped_shouldBuildLogForSkippedDocument() {
        var log = factory.createForSkipped(
            "doc-123", "test.pdf", "trace-456",
            DocumentStatus.SKIPPED.name(), "SKIPPED_FOLDER", 0);

        assertThat(log.getTraceId()).isEqualTo("trace-456");
        assertThat(log.getDocumentId()).isEqualTo("doc-123");
        assertThat(log.getStatus()).isEqualTo(DocumentStatus.SKIPPED.name());
        assertThat(log.getErrorCode()).isEqualTo("SKIPPED_FOLDER");
        assertThat(log.getFilename()).isEqualTo("test.pdf");
        assertThat(log.getGatewayName()).isEqualTo("SOAP");
        assertThat(log.getLatencyMs()).isEqualTo(0L);
    }
}
