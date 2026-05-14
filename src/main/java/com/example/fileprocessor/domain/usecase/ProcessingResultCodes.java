package com.example.fileprocessor.domain.usecase;

/**
 * Single source of truth for all processing result codes and error
 * classifications.
 * Replaces SoapErrorCodes, S3ErrorCodes and DocumentStatus, which have been
 * deleted.
 */
public enum ProcessingResultCodes {

    // ── Result states ──────────────────────────────────────────────────────────
    SUCCESS("Operation completed successfully"),
    FAILURE("Generic processing failure"),
    IN_PROGRESS("Operation is currently being processed"),

    // ── Business rule errors (non-retryable) ───────────────────────────────────
    INVALID_BASE64("File content is not valid Base64"),
    EMPTY_CONTENT("File is 0 bytes"),
    INVALID_RESPONSE("Unexpected response from source API"),
    DECOMPRESSION_ERROR("ZIP is corrupt or password-protected"),
    SIZE_EXCEEDED("File exceeds the maximum allowed size"),
    PATTERN_MISMATCH("Extension/MIME type not supported by business rules"),
    HOMOLOGATION_FAILED("Could not map metadata to the SOAP contract"),

    // ── Network / transient errors (retryable) ─────────────────────────────────
    BAD_GATEWAY("Source or destination responded with 5xx"),
    GATEWAY_TIMEOUT("Timeout reaching source or destination"),
    SERVICE_UNAVAILABLE("Service is temporarily down (502/503)"),

    // ── Source system errors ───────────────────────────────────────────────────
    SOURCE_NOT_FOUND("Document does not exist in source (404)"),
    SOURCE_UNAUTHORIZED("Credentials rejected by source (401/403)"),
    SOURCE_RATE_LIMIT("Rate limit exceeded in source (429)"),

    // ── Destination errors ─────────────────────────────────────────────────────
    DEST_BAD_REQUEST("Destination rejected our payload (400)"),
    DEST_UNAUTHORIZED("Authentication failed against SOAP/S3 destination"),
    DEST_QUOTA_EXCEEDED("Storage quota exceeded in destination"),

    // ── Final states ───────────────────────────────────────────────────────────
    MAX_RETRIES_EXCEEDED("All retry attempts exhausted"),
    SKIPPED("Omitted due to business rules"),
    ERROR("High-level technical failure"),
    SOAP_ERROR("Specific business fault returned by SOAP gateway"),
    UNKNOWN_ERROR("Critical unclassified error");

    private final String value;

    ProcessingResultCodes(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static boolean isBusinessRule(String code) {
        if (code == null) return false;
        try {
            ProcessingResultCodes res = valueOf(code);
            return switch (res) {
                case INVALID_BASE64, EMPTY_CONTENT, DECOMPRESSION_ERROR, 
                     SIZE_EXCEEDED, PATTERN_MISMATCH, HOMOLOGATION_FAILED, SKIPPED -> true;
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
                case BAD_GATEWAY, GATEWAY_TIMEOUT, SERVICE_UNAVAILABLE, UNKNOWN_ERROR -> true;
                default -> false;
            };
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
