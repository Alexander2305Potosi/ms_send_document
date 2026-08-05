package com.example.fileprocessor.domain.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SanitizationUtilsTest {

    @Test
    void sanitizeRemovesAccentsAndConvertsToLowercase() {
        assertNull(SanitizationUtils.sanitize(null));
        assertEquals("bogota", SanitizationUtils.sanitize("Bogotá"));
        assertEquals("antioquia", SanitizationUtils.sanitize("Antioquía"));
        assertEquals("boyaca", SanitizationUtils.sanitize("Boyacá"));
        assertEquals("arbolverde 100", SanitizationUtils.sanitize("ÁrbolVerde 100"));
    }

    @Test
    void sanitizeKeepingTextPreservesCasingAndAccentsWhileRemovingSpecialChars() {
        assertNull(SanitizationUtils.sanitizeKeepingText(null));
        assertEquals("ÁrbolVerde 100", SanitizationUtils.sanitizeKeepingText("  Árbol-Verde, $100!  "));
        assertEquals("ATENCIÓN El niño de la España del siglo XXI", SanitizationUtils.sanitizeKeepingText("¡ATENCIÓN! El niño de la España del siglo XXI."));
    }
}
