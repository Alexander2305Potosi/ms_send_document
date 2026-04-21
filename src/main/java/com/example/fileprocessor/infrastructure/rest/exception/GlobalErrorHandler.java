package com.example.fileprocessor.infrastructure.rest.exception;

import com.example.fileprocessor.domain.exception.FileValidationException;
import com.example.fileprocessor.infrastructure.soap.exception.SoapCommunicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

    @ExceptionHandler(DataBufferLimitException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleDataBufferLimitException(DataBufferLimitException ex) {
        log.warn("Request payload too large: {}", ex.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(createErrorResponse(HttpStatus.PAYLOAD_TOO_LARGE, "Request payload too large", "PAYLOAD_TOO_LARGE")));
    }

    @ExceptionHandler(FileValidationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidationException(FileValidationException ex) {
        log.warn("File validation error: {}", ex.getMessage());

        return Mono.just(ResponseEntity.badRequest()
            .body(createErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getErrorCode())));
    }

    @ExceptionHandler(SoapCommunicationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleSoapException(SoapCommunicationException ex) {
        log.error("SOAP communication error [{}]: {}", ex.getErrorCode(), ex.getMessage());

        HttpStatus status = switch (ex.getErrorCode()) {
            case "GATEWAY_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
            case "BAD_GATEWAY" -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        return Mono.just(ResponseEntity.status(status)
            .body(createErrorResponse(status, ex.getMessage(), ex.getErrorCode(), ex.getTraceId())));
    }

    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleServerWebInputException(ServerWebInputException ex) {
        log.warn("Web exchange binding error: {}", ex.getMessage());

        return Mono.just(ResponseEntity.badRequest()
            .body(createErrorResponse(HttpStatus.BAD_REQUEST, "Invalid request format", "INVALID_REQUEST")));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());

        return Mono.just(ResponseEntity.badRequest()
            .body(createErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), "INVALID_ARGUMENT")));
    }

    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalStateException(IllegalStateException ex) {
        if (reactor.core.Exceptions.isRetryExhausted(ex)) {
            Throwable cause = reactor.core.Exceptions.unwrap(ex);
            log.error("Retry exhausted: {}", cause.getMessage());
            return Mono.just(ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(createErrorResponse(HttpStatus.GATEWAY_TIMEOUT, "Request timed out after retries", "GATEWAY_TIMEOUT")));
        }
        return handleGenericException(ex);
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);

        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "INTERNAL_ERROR")));
    }

    private Map<String, Object> createErrorResponse(HttpStatus status, String message, String errorCode) {
        return createErrorResponse(status, message, errorCode, MDC.get("traceId"));
    }

    private Map<String, Object> createErrorResponse(HttpStatus status, String message, String errorCode, String traceId) {
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", Instant.now().toString());
        error.put("status", status.value());
        error.put("error", status.getReasonPhrase());
        error.put("message", message);
        error.put("errorCode", errorCode);
        if (traceId != null) {
            error.put("traceId", traceId);
        }
        return error;
    }
}
