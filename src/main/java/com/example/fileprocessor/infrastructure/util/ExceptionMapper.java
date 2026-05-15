package com.example.fileprocessor.infrastructure.util;

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
        String errorCode = ProcessingResultCodes.UNKNOWN_ERROR.name();
        String message = error.getMessage();

        if (error instanceof ProcessingException pe) {
            errorCode = pe.getErrorCode();
        } else if (error instanceof WebClientResponseException wcre) {
            errorCode = mapHttpStatusCode(wcre.getStatusCode().value());
            message = "API Error: " + wcre.getStatusCode() + " - " + wcre.getResponseBodyAsString();
        } else if (isTimeout(error)) {
            errorCode = ProcessingResultCodes.GATEWAY_TIMEOUT.name();
            message = ProcessingResultCodes.GATEWAY_TIMEOUT.value();
        } else if (isConnectionError(error)) {
            errorCode = ProcessingResultCodes.SERVICE_UNAVAILABLE.name();
            message = ProcessingResultCodes.SERVICE_UNAVAILABLE.value();
        }

        if (message == null || message.isBlank()) {
            message = error.getClass().getSimpleName();
        }

        return new ErrorClassification(errorCode, message);
    }

    private static String mapHttpStatusCode(int statusCode) {
        return switch (statusCode) {
            case 400 -> ProcessingResultCodes.DEST_BAD_REQUEST.name();
            case 401, 403 -> ProcessingResultCodes.DEST_UNAUTHORIZED.name();
            case 404 -> ProcessingResultCodes.SOURCE_NOT_FOUND.name();
            case 429 -> ProcessingResultCodes.SOURCE_RATE_LIMIT.name();
            default -> (statusCode >= 500) ? ProcessingResultCodes.BAD_GATEWAY.name()
                    : ProcessingResultCodes.UNKNOWN_ERROR.name();
        };
    }

    private static boolean isTimeout(Throwable e) {
        return e instanceof TimeoutException || e.getCause() instanceof TimeoutException;
    }

    private static boolean isConnectionError(Throwable e) {
        return e instanceof ConnectException || e.getCause() instanceof ConnectException;
    }
}
