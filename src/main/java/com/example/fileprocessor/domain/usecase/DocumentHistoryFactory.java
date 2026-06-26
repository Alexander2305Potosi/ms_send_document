package com.example.fileprocessor.domain.usecase;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.BUSINESS_REJECTION;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILED;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILURE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PENDING;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PROCESSED;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.UNKNOWN_ERROR;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.exception.ProcessingException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public final class DocumentHistoryFactory {

    private DocumentHistoryFactory() {
        // Prevent instantiation
    }

    public record ProcessingConclusion(String nextState, int nextRetryCount) {}

    public static ProcessingConclusion calculateNextState(int currentRetry, List<FileUploadResponse> responses) {
        ProcessingConclusion conclusion;
        if (responses.isEmpty()) {
            conclusion = new ProcessingConclusion(FAILED.name(), currentRetry);
        } else if (responses.stream().allMatch(FileUploadResponse::isSuccess)) {
            conclusion = new ProcessingConclusion(PROCESSED.name(), currentRetry);
        } else if (responses.stream().anyMatch(r -> ProcessingResultCodes.isBusinessRule(r.getSyncStatus()))) {
            conclusion = new ProcessingConclusion(BUSINESS_REJECTION.name(), currentRetry);
        } else if (currentRetry < ProcessingResultCodes.MAX_RETRIES && responses.stream().anyMatch(r -> ProcessingResultCodes.isTransient(r.getSyncStatus()))) {
            conclusion = new ProcessingConclusion(PENDING.name(), currentRetry + 1);
        } else {
            conclusion = new ProcessingConclusion(FAILED.name(), currentRetry);
        }
        return conclusion;
    }

    public static DocumentHistoryDTO syncHistoryDTO(Document doc, DocumentHistoryDTO fileHistory, FileUploadResponse response) {
        String traceId = response.getTraceId();
        String syncMessage = response.getMessage();
        if (traceId != null && !traceId.isBlank() && !"unknown".equals(traceId)) {
            syncMessage = (syncMessage != null ? syncMessage : "") + " [TraceID: " + traceId + "]";
        }
        return fileHistory.toBuilder()
                .documentId(doc.getId())
                .state(calculateFileState(response))
                .useCase(doc.getUseCase())
                .retryCount(response.getAttemptCount() > 0 ? response.getAttemptCount() : doc.getRetryCountSafe())
                .businessRetryCount(doc.getRetryCountSafe())
                .filename(Boolean.TRUE.equals(doc.getIsZip()) ? (response.getFilename() != null ? response.getFilename() : fileHistory.getFilename()) : null)
                .syncStatus(response.getSyncStatus())
                .syncMessage(syncMessage)
                .completedAt(Instant.now())
                .build();
    }

    public static DocumentHistoryDTO syncGlobalHistory(
            Document doc,
            DocumentHistoryDTO history,
            List<FileUploadResponse> responses,
            ProcessingConclusion conclusion) {
        String representativeStatus = responses.stream()
                .map(FileUploadResponse::getSyncStatus)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        String traceId = responses.stream()
                .map(FileUploadResponse::getTraceId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        String syncMessage;
        if (Boolean.TRUE.equals(doc.getIsZip())) {
            syncMessage = aggregateMessages(responses);
        } else {
            if (responses.isEmpty()) {
                syncMessage = "";
            } else {
                FileUploadResponse r = responses.getFirst();
                syncMessage = r.getMessage() != null ? r.getMessage() : (r.isSuccess() ? "SUCCESS" : r.getSyncStatus());
            }
            if (traceId != null && !traceId.isBlank() && !"unknown".equals(traceId)) {
                syncMessage = (syncMessage != null ? syncMessage : "") + " [TraceID: " + traceId + "]";
            }
        }

        int attempt = responses.stream()
                .map(FileUploadResponse::getAttemptCount)
                .max(Integer::compareTo)
                .orElse(0);

        return history.toBuilder()
                .documentId(doc.getId())
                .state(conclusion.nextState())
                .useCase(doc.getUseCase())
                .retryCount(attempt > 0 ? attempt : doc.getRetryCountSafe())
                .businessRetryCount(conclusion.nextRetryCount())
                .filename(Boolean.TRUE.equals(doc.getIsZip()) ? history.getFilename() : null)
                .syncStatus(representativeStatus)
                .syncMessage(syncMessage)
                .completedAt(Instant.now())
                .build();
    }

    public static String calculateFileState(FileUploadResponse response) {
        if (response.isSuccess()) {
            return PROCESSED.name();
        }
        if (ProcessingResultCodes.isBusinessRule(response.getSyncStatus())) {
            return BUSINESS_REJECTION.name();
        }
        return PENDING.name();
    }

    public static String aggregateMessages(List<FileUploadResponse> responses) {
        return responses.stream()
                .map(r -> {
                    String filename = r.getFilename() != null ? r.getFilename() : "unknown";
                    String trace = (r.getTraceId() != null && !r.getTraceId().isBlank() && !"unknown".equals(r.getTraceId())) ? r.getTraceId() : "N/A";
                    String detail = r.getMessage() != null ? r.getMessage() : (r.isSuccess() ? "SUCCESS" : r.getSyncStatus());
                    return String.format("[Archivo: %s | TraceID: %s | Detalle: %s]", filename, trace, detail);
                })
                .collect(Collectors.joining(" || "));
    }

    /**
     * Builds a failure {@link FileUploadResponse} from any error that escaped adapter-level
     * handling. At this point, network/HTTP errors should already have been translated into
     * {@link ProcessingException} by the adapter layer (via {@code AdapterErrorMapper}).
     *
     * <p>This method is responsible only for domain-level error extraction.</p>
     */
    public static FileUploadResponse handleGlobalError(Throwable error) {
        // Unwrap until we find a recognized domain exception
        Throwable root = error;
        while (root.getCause() != null && root != root.getCause()) {
            if (root instanceof ProcessingException) {
                break;
            }
            root = root.getCause();
        }

        String syncStatus = UNKNOWN_ERROR.name();
        String message = root.getMessage();
        String filename = null;

        if (root instanceof ProcessingException pe) {
            syncStatus = pe.getErrorCode() != null && !pe.getErrorCode().isBlank()
                    ? pe.getErrorCode()
                    : UNKNOWN_ERROR.name();
            message = pe.getMessage();
            filename = pe.getFilename();
        }

        String finalMsg = message != null && !message.isBlank() ? message : error.getMessage();

        return FileUploadResponse.builder()
                .status(FAILURE.name())
                .syncStatus(syncStatus)
                .message(finalMsg != null && !finalMsg.isBlank() ? finalMsg : UNKNOWN_ERROR.value())
                .processedAt(Instant.now())
                .filename(filename)
                .success(false)
                .build();
    }

    public static ProcessingException mapValidationError(Throwable e, DocumentHistoryDTO masterHistory, DocumentHistoryDTO innerHistory) {
        ProcessingException pe;
        if (e instanceof ProcessingException existingPe) {
            pe = existingPe;
            if (pe.getErrorCode() == null || pe.getErrorCode().isBlank()) {
                pe = new ProcessingException(pe.getMessage(),
                        UNKNOWN_ERROR.name(), pe.getCause());
            }
        } else {
            pe = new ProcessingException(e.getMessage(),
                    UNKNOWN_ERROR.name(), e);
        }
        if (Boolean.TRUE.equals(masterHistory.getIsZip())) {
            pe.setFilename(innerHistory.getFilename());
        }
        return pe;
    }
}
