package com.example.fileprocessor.domain.util;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.EMPTY_CONTENT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.INVALID_BASE64;

import com.example.fileprocessor.domain.exception.InvalidBase64Exception;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;

import java.util.Base64;

/**
 * Utility class for Base64 encoding/decoding.
 */
public final class Base64Utils {

    private Base64Utils() {}

    public static byte[] decodeSafe(String encoded, String filename, String documentId) {
        if (encoded == null || encoded.isBlank()) {
            throw new InvalidBase64Exception(
                "Empty Base64 content for document: " + filename + " (documentId=" + documentId + ")",
                EMPTY_CONTENT.name());
        }
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new InvalidBase64Exception(
                "Invalid Base64 content for document: " + filename
                + " (documentId=" + documentId + "): " + e.getMessage(),
                INVALID_BASE64.name(), e);
        }
    }

    public static String encode(byte[] content) {
        if (content == null) {
            return "";
        }
        return Base64.getEncoder().encodeToString(content);
    }
}
