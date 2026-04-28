package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.CommunicationLog;
import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Factory for creating CommunicationLog entries.
 * Single point of construction for all communication logs.
 */
public class CommunicationLogFactory {

    private final String gatewayName;

    public CommunicationLogFactory(String gatewayName) {
        this.gatewayName = gatewayName;
    }

    /**
     * Creates a CommunicationLog entry from a request and result.
     */
    public CommunicationLog create(
            DocumentSendRequest request,
            FileUploadResult result,
            int retryCount,
            Instant startTime,
            Map<String, Object> additionalMetadata) {

        long latencyMs = Duration.between(startTime, Instant.now()).toMillis();
        String metadata = serializeMetadata(additionalMetadata);

        return CommunicationLog.builder()
            .traceId(request.getTraceId())
            .documentId(request.getDocumentId())
            .status(result.getStatus())
            .retryCount(retryCount)
            .errorCode(result.getErrorCode())
            .filename(request.getFilename())
            .createdAt(Instant.now())
            .latencyMs(latencyMs)
            .gatewayName(gatewayName)
            .metadata(metadata)
            .build();
    }

    /**
     * Creates a CommunicationLog from a document ID and filename (for skipped documents).
     */
    public CommunicationLog createForSkipped(
            String documentId,
            String filename,
            String traceId,
            String status,
            String errorCode,
            int retryCount) {

        return CommunicationLog.builder()
            .traceId(traceId)
            .documentId(documentId)
            .status(status)
            .retryCount(retryCount)
            .errorCode(errorCode)
            .filename(filename)
            .createdAt(Instant.now())
            .latencyMs(0L)
            .gatewayName(gatewayName)
            .metadata("{}")
            .build();
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        metadata.forEach((key, value) -> {
            if (sb.length() > 1) sb.append(",");
            sb.append("\"").append(key).append("\":\"").append(value).append("\"");
        });
        sb.append("}");
        return sb.toString();
    }
}
