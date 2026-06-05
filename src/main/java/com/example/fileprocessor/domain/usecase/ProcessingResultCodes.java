package com.example.fileprocessor.domain.usecase;

import java.util.Set;

/**
 * Single source of truth for all processing states and result codes.
 * Internally discriminates between Table States (documentos) and Result Details (historico_documentos).
 */
public enum ProcessingResultCodes {

    // ── Table States (documentos table) ──────────────────────────────────────
    PENDING("Document is waiting to be processed"),
    IN_PROGRESS("Document is currently being processed"),
    PROCESSED("Document successfully uploaded and finalized"),
    FAILED("Document failed all processing attempts"),
    ERR_DUPLICATED_DOC("Document skipped because it already exists"),
    BUSINESS_REJECTION("Document rejected by business rules validation"),

    // ── Result Details (historico_documentos table) ──────────────────────────
    SUCCESS("Operation completed successfully"),
    FAILURE("Generic processing failure"),
    RETRYABLE_ERROR("Operation failed but will be retried"),

    // Business rule errors (non-retryable)
    INVALID_BASE64("File content is not valid Base64"),
    EMPTY_CONTENT("File is 0 bytes"),
    INVALID_RESPONSE("Unexpected response from source API"),
    DECOMPRESSION_ERROR("ZIP is corrupt or password-protected"),
    SIZE_EXCEEDED("File exceeds the maximum allowed size"),
    PATTERN_MISMATCH("Extension/MIME type not supported by business rules"),

    // Network / transient errors (retryable)
    BAD_GATEWAY("Source or destination responded with 5xx"),
    GATEWAY_TIMEOUT("Timeout reaching source or destination"),
    SERVICE_UNAVAILABLE("Service is temporarily down (502/503)"),

    // Source system errors
    SOURCE_NOT_FOUND("Document does not exist in source (404)"),
    SOURCE_RATE_LIMIT("Rate limit exceeded in source (429)"),

    // Destination errors
    DEST_BAD_REQUEST("Destination rejected our payload (400)"),
    DEST_UNAUTHORIZED("Authentication failed against SOAP/S3 destination"),

    // Final technical states
    SKIPPED("Omitted due to business rules"),
    ERROR("High-level technical failure"),
    SOAP_ERROR("Specific business fault returned by SOAP gateway"),
    UNKNOWN_ERROR("Critical unclassified error"),
    NO_SUCURSAL("No se encontró sucursal");

    private final String description;

    ProcessingResultCodes(String description) {
        this.description = description;
    }

    public String value() {
        return description;
    }

    /**
     * Set of codes that represent valid states for the 'documentos' table.
     */
    private static final Set<ProcessingResultCodes> TABLE_STATES = Set.of(
        PENDING, IN_PROGRESS, PROCESSED, FAILED, ERR_DUPLICATED_DOC
    );

    public boolean isTableState() {
        return TABLE_STATES.contains(this);
    }

    public static boolean isBusinessRule(String code) {
        if (code == null) return false;
        try {
            ProcessingResultCodes res = valueOf(code);
            return switch (res) {
                case INVALID_BASE64, EMPTY_CONTENT, DECOMPRESSION_ERROR, 
                     SIZE_EXCEEDED, PATTERN_MISMATCH, SKIPPED, ERR_DUPLICATED_DOC -> true;
                default -> false;
            };
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isTransient(String code) {
        if (code == null) return false;
        try {
            ProcessingResultCodes res = valueOf(code);
            return switch (res) {
                case BAD_GATEWAY, GATEWAY_TIMEOUT, SERVICE_UNAVAILABLE, UNKNOWN_ERROR, RETRYABLE_ERROR -> true;
                default -> false;
            };
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
