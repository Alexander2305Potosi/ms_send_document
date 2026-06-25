package com.example.fileprocessor.domain.usecase;



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

    public static final int MAX_RETRIES = 3;

    private static final java.util.Set<String> BUSINESS_RULES = java.util.Set.of(
        INVALID_BASE64.name(), EMPTY_CONTENT.name(), DECOMPRESSION_ERROR.name(), 
        SIZE_EXCEEDED.name(), PATTERN_MISMATCH.name(), SKIPPED.name(), 
        ERR_DUPLICATED_DOC.name(), BUSINESS_REJECTION.name()
    );

    private static final java.util.Set<String> TRANSIENT_ERRORS = java.util.Set.of(
        BAD_GATEWAY.name(), GATEWAY_TIMEOUT.name(), SERVICE_UNAVAILABLE.name(), RETRYABLE_ERROR.name()
    );

    ProcessingResultCodes(String description) {
        this.description = description;
    }

    public String value() {
        return description;
    }

    public static boolean isBusinessRule(String code) {
        if (code == null) return false;
        return BUSINESS_RULES.contains(code);
    }

    public static boolean isTransient(String code) {
        if (code == null) return false;
        return TRANSIENT_ERRORS.contains(code);
    }
}
