package com.example.fileprocessor.infrastructure.drivenadapters;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.BAD_GATEWAY;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILURE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.GATEWAY_TIMEOUT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SERVICE_UNAVAILABLE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SOURCE_NOT_FOUND;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SOURCE_RATE_LIMIT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.UNKNOWN_ERROR;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

/**
 * Centralizes error-to-domain-code mapping for all driven adapters (REST, SOAP, and future adapters).
 *
 * <p>This class belongs to the infrastructure layer and is the single place responsible for
 * translating technical API/network errors into {@link ProcessingResultCodes}. By keeping this
 * logic here, use cases and domain factories remain free of infrastructure concerns.</p>
 *
 * <h3>Usage pattern (in any adapter):</h3>
 * <pre>{@code
 *   .onErrorResume(error -> AdapterErrorMapper.toErrorResponse(error, traceId))
 * }</pre>
 *
 * <h3>Error mapping rules:</h3>
 * <ul>
 *   <li>{@link ProcessingException} — already carries a domain error code, re-emitted as-is.</li>
 *   <li>HTTP 404 → {@code SOURCE_NOT_FOUND}</li>
 *   <li>HTTP 429 → {@code SOURCE_RATE_LIMIT}</li>
 *   <li>HTTP 5xx → {@code BAD_GATEWAY}</li>
 *   <li>Other HTTP → {@code UNKNOWN_ERROR}</li>
 *   <li>{@link TimeoutException} or message containing "timeout" → {@code GATEWAY_TIMEOUT}</li>
 *   <li>{@link ConnectException} → {@code SERVICE_UNAVAILABLE}</li>
 *   <li>Anything else → {@code UNKNOWN_ERROR}</li>
 * </ul>
 */
public final class AdapterErrorMapper {

    private AdapterErrorMapper() {
        // Utility class — no instantiation
    }

    /**
     * Maps any {@link Throwable} thrown by an adapter call into a {@link FileUploadResponse}
     * with the appropriate {@link ProcessingResultCodes} and wraps it in a {@link Mono}.
     *
     * <p>If the error is already a {@link ProcessingException} with a non-blank error code,
     * its code is preserved so no domain semantics are overwritten.</p>
     *
     * @param error   the exception thrown by the adapter
     * @param traceId the current request trace identifier (may be null)
     * @return a {@link Mono} that emits a failure {@link FileUploadResponse}
     */
    public static Mono<FileUploadResponse> toErrorResponse(Throwable error, String traceId) {
        return Mono.just(buildErrorResponse(error, traceId));
    }

    /**
     * Synchronous variant — builds the {@link FileUploadResponse} directly without wrapping in Mono.
     *
     * @param error   the exception thrown by the adapter
     * @param traceId the current request trace identifier (may be null)
     * @return a failure {@link FileUploadResponse}
     */
    public static FileUploadResponse buildErrorResponse(Throwable error, String traceId) {
        Throwable root = unwrap(error);

        String syncStatus;
        String message;

        if (root instanceof ProcessingException pe && pe.getErrorCode() != null && !pe.getErrorCode().isBlank()) {
            syncStatus = pe.getErrorCode();
            message = pe.getMessage();
        } else if (root instanceof WebClientResponseException wce) {
            syncStatus = mapHttpStatus(wce);
            message = String.format("HTTP %d - %s", wce.getStatusCode().value(), wce.getStatusText());
        } else if (isTimeout(root)) {
            syncStatus = GATEWAY_TIMEOUT.name();
            message = "Timeout: El servicio no respondió a tiempo";
        } else if (root instanceof ConnectException) {
            syncStatus = SERVICE_UNAVAILABLE.name();
            message = "Connection refused: El servicio no está disponible";
        } else {
            syncStatus = UNKNOWN_ERROR.name();
            message = root.getMessage() != null && !root.getMessage().isBlank() ? root.getMessage() : error.getMessage();
        }

        return FileUploadResponse.builder()
                .status(FAILURE.name())
                .syncStatus(syncStatus)
                .message(message != null && !message.isBlank() ? message : UNKNOWN_ERROR.value())
                .traceId(traceId)
                .processedAt(Instant.now())
                .success(false)
                .build();
    }

    /**
     * Resolves only the {@link ProcessingResultCodes} name for the given error.
     * Useful when the caller needs to build its own response object.
     *
     * @param error the exception thrown by the adapter
     * @return the matching {@link ProcessingResultCodes} name
     */
    public static String resolveErrorCode(Throwable error) {
        Throwable root = unwrap(error);

        if (root instanceof ProcessingException pe && pe.getErrorCode() != null && !pe.getErrorCode().isBlank()) {
            return pe.getErrorCode();
        }
        if (root instanceof WebClientResponseException wce) {
            return mapHttpStatus(wce);
        }
        if (isTimeout(root)) {
            return GATEWAY_TIMEOUT.name();
        }
        if (root instanceof ConnectException) {
            return SERVICE_UNAVAILABLE.name();
        }
        return UNKNOWN_ERROR.name();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Unwraps the cause chain until reaching a well-known exception type or the root.
     * Stops early at {@link ProcessingException} and {@link WebClientResponseException}
     * to avoid masking already-enriched errors.
     */
    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current != current.getCause()) {
            if (current instanceof ProcessingException || current instanceof WebClientResponseException) {
                break;
            }
            current = current.getCause();
        }
        return current;
    }

    private static String mapHttpStatus(WebClientResponseException wce) {
        int status = wce.getStatusCode().value();
        if (status == 404) return SOURCE_NOT_FOUND.name();
        if (status == 429) return SOURCE_RATE_LIMIT.name();
        if (wce.getStatusCode().is5xxServerError()) return BAD_GATEWAY.name();
        return UNKNOWN_ERROR.name();
    }

    private static boolean isTimeout(Throwable t) {
        return t instanceof TimeoutException
                || (t.getMessage() != null && t.getMessage().toLowerCase().contains("timeout"));
    }
}
