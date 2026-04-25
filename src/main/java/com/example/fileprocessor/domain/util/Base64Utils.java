package com.example.fileprocessor.domain.util;

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

    public static String encode(byte[] content) {
        if (content == null) {
            return "";
        }
        return Base64.getEncoder().encodeToString(content);
    }
}
