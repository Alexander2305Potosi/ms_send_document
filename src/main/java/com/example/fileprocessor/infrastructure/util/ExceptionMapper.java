package com.example.fileprocessor.infrastructure.util;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.BAD_GATEWAY;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.DEST_BAD_REQUEST;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.DEST_UNAUTHORIZED;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.GATEWAY_TIMEOUT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SERVICE_UNAVAILABLE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SOURCE_NOT_FOUND;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SOURCE_RATE_LIMIT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.UNKNOWN_ERROR;

import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

/**
 * Infrastructure utility to map technical exceptions to domain result codes.
 */
public final class ExceptionMapper {

    private ExceptionMapper() {
        // Utility class
    }

    public record ErrorClassification(String code, String message) {
    }

    public static ErrorClassification classify(Throwable error) {
        String syncStatus = UNKNOWN_ERROR.name();
        String message = error.getMessage();

        if (error instanceof ProcessingException pe) {
            syncStatus = pe.getErrorCode();
        } else if (error instanceof WebClientResponseException wcre) {
            syncStatus = mapHttpStatusCode(wcre.getStatusCode().value());
            message = "API Error: " + wcre.getStatusCode() + " - " + wcre.getResponseBodyAsString();
        } else if (isTimeout(error)) {
            syncStatus = GATEWAY_TIMEOUT.name();
            message = GATEWAY_TIMEOUT.value();
        } else if (isConnectionError(error)) {
            syncStatus = SERVICE_UNAVAILABLE.name();
            message = SERVICE_UNAVAILABLE.value();
        }

        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }

        return new ErrorClassification(syncStatus, message);
    }

    private static String mapHttpStatusCode(int statusCode) {
        return switch (statusCode) {
            case 400 -> DEST_BAD_REQUEST.name();
            case 401, 403 -> DEST_UNAUTHORIZED.name();
            case 404 -> SOURCE_NOT_FOUND.name();
            case 429 -> SOURCE_RATE_LIMIT.name();
            default -> (statusCode >= 500) ? BAD_GATEWAY.name()
                    : UNKNOWN_ERROR.name();
        };
    }

    private static boolean isTimeout(Throwable e) {
        return e instanceof TimeoutException || e.getCause() instanceof TimeoutException;
    }

    private static boolean isConnectionError(Throwable e) {
        return e instanceof ConnectException || e.getCause() instanceof ConnectException;
    }
}
