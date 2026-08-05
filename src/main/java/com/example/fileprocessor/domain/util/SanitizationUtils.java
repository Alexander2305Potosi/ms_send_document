package com.example.fileprocessor.domain.util;

import java.text.Normalizer;

/**
 * Utility class for text sanitization.
 */
public final class SanitizationUtils {

    private SanitizationUtils() {}

    /**
     * Normalizes and removes accents/diacritics and converts to lowercase.
     *
     * @param text the input text
     * @return the sanitized lowercase text, or null if input is null
     */
    public static String sanitize(String text) {
        if (text == null) return null;
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase();
    }

    /**
     * Preserves letters (including case and accents), numbers and spaces, removing everything else.
     *
     * @param text the input text
     * @return the sanitized text with special characters removed, or null if input is null
     */
    public static String sanitizeKeepingText(String text) {
        if (text == null) return null;
        // Conserva todas las letras (incluyendo mayúsculas, minúsculas y caracteres acentuados),
        // números y espacios. Elimina todo lo demás.
        return text.replaceAll("[^\\p{L}\\p{N} ]", "").trim();
    }
}
