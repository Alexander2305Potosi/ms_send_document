package com.example.fileprocessor.domain.util;

import com.example.fileprocessor.domain.exception.InvalidBase64Exception;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for Base64 encoding/decoding.
 */
public final class Base64Utils {

    private Base64Utils() {}

    public static byte[] decode(String base64Content) {
        if (base64Content == null || base64Content.isBlank()) {
            return null;
        }
        return Base64.getDecoder().decode(base64Content);
    }

    public static byte[] decodeSafe(String encoded, String filename, String documentId) {
        if (encoded == null || encoded.isBlank()) {
            throw new InvalidBase64Exception(
                "Empty Base64 content for document: " + filename + " (documentId=" + documentId + ")",
                ProcessingResultCodes.EMPTY_CONTENT);
        }
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new InvalidBase64Exception(
                "Invalid Base64 content for document: " + filename
                + " (documentId=" + documentId + "): " + e.getMessage(),
                ProcessingResultCodes.INVALID_BASE64, e);
        }
    }

    public static boolean isValidUtf8(byte[] decoded, String filename) {
        if (decoded == null) return false;
        try {
            new String(decoded, StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String encode(byte[] content) {
        if (content == null) {
            return "";
        }
        return Base64.getEncoder().encodeToString(content);
    }
}