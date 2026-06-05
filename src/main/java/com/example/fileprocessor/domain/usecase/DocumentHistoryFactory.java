package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.exception.ProcessingException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public final class DocumentHistoryFactory {

    private static final int MAX_RETRIES = 3;

    private DocumentHistoryFactory() {
        // Prevent instantiation
    }

    public record ProcessingConclusion(String nextState, int nextRetryCount) {}

    public static ProcessingConclusion calculateNextState(int currentRetry, List<FileUploadResponse> responses) {
        ProcessingConclusion conclusion;
        if (responses.isEmpty()) {
            conclusion = new ProcessingConclusion(ProcessingResultCodes.FAILED.name(), currentRetry);
        } else if (responses.stream().allMatch(FileUploadResponse::isSuccess)) {
            conclusion = new ProcessingConclusion(ProcessingResultCodes.PROCESSED.name(), currentRetry);
        } else if (responses.stream().anyMatch(r -> ProcessingResultCodes.isBusinessRule(r.getSyncStatus()))) {
            conclusion = new ProcessingConclusion(ProcessingResultCodes.BUSINESS_REJECTION.name(), currentRetry);
        } else if (currentRetry < MAX_RETRIES && responses.stream().anyMatch(r -> ProcessingResultCodes.isTransient(r.getSyncStatus()))) {
            conclusion = new ProcessingConclusion(ProcessingResultCodes.PENDING.name(), currentRetry + 1);
        } else {
            conclusion = new ProcessingConclusion(ProcessingResultCodes.FAILED.name(), currentRetry);
        }
        return conclusion;
    }

    public static DocumentHistoryDTO syncHistoryDTO(Document doc, DocumentHistoryDTO fileHistory, FileUploadResponse response) {
        int effectiveRetry = response.getAttemptCount() > 0 ? response.getAttemptCount() : doc.getRetryCountSafe();
        return fileHistory.toBuilder()
                .documentId(doc.getId())
                .state(calculateFileState(response))
                .useCase(doc.getUseCase())
                .retryCount(effectiveRetry)
                .businessRetryCount(doc.getRetryCountSafe())
                .filename(response.getFilename() != null ? response.getFilename() : fileHistory.getFilename())
                .syncStatus(response.getSyncStatus())
                .syncMessage(response.getMessage())
                .completedAt(Instant.now())
                .build();
    }

    public static DocumentHistoryDTO syncGlobalHistory(
            Document doc,
            DocumentHistoryDTO history,
            List<FileUploadResponse> responses,
            ProcessingConclusion conclusion) {
        String representativeStatus = responses.isEmpty() ? null : responses.get(0).getSyncStatus();
        String syncMessage = aggregateMessages(responses);

        return history.toBuilder()
                .documentId(doc.getId())
                .state(conclusion.nextState())
                .useCase(doc.getUseCase())
                .retryCount(doc.getRetryCountSafe())
                .businessRetryCount(conclusion.nextRetryCount())
                .filename(history.getFilename())
                .syncStatus(representativeStatus)
                .syncMessage(syncMessage)
                .completedAt(Instant.now())
                .build();
    }

    public static String calculateFileState(FileUploadResponse response) {
        if (response.isSuccess()) {
            return ProcessingResultCodes.PROCESSED.name();
        }
        if (ProcessingResultCodes.isBusinessRule(response.getSyncStatus())) {
            return ProcessingResultCodes.BUSINESS_REJECTION.name();
        }
        return ProcessingResultCodes.PENDING.name();
    }

    public static String aggregateMessages(List<FileUploadResponse> responses) {
        return responses.stream()
                .map(r -> String.format("%s: %s",
                        r.getFilename() != null ? r.getFilename() : "unknown",
                        r.getMessage() != null ? r.getMessage() : (r.isSuccess() ? "SUCCESS" : r.getSyncStatus())))
                .collect(Collectors.joining(" | "));
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

        String syncStatus = ProcessingResultCodes.UNKNOWN_ERROR.name();
        String message = root.getMessage();
        String filename = null;

        if (root instanceof ProcessingException pe) {
            syncStatus = pe.getErrorCode() != null && !pe.getErrorCode().isBlank()
                    ? pe.getErrorCode()
                    : ProcessingResultCodes.UNKNOWN_ERROR.name();
            message = pe.getMessage();
            filename = pe.getFilename();
        }

        String finalMsg = message != null && !message.isBlank() ? message : error.getMessage();

        return FileUploadResponse.builder()
                .status(ProcessingResultCodes.FAILURE.name())
                .syncStatus(syncStatus)
                .message(finalMsg != null && !finalMsg.isBlank() ? finalMsg : ProcessingResultCodes.UNKNOWN_ERROR.value())
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
                        ProcessingResultCodes.UNKNOWN_ERROR.name(), pe.getCause());
            }
        } else {
            pe = new ProcessingException(e.getMessage(),
                    ProcessingResultCodes.UNKNOWN_ERROR.name(), e);
        }
        if (Boolean.TRUE.equals(masterHistory.getIsZip())) {
            pe.setFilename(innerHistory.getFilename());
        }
        return pe;
    }
}
