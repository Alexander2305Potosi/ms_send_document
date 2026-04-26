package com.example.fileprocessor.domain.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MediaTypeConstantsTest {

    @Test
    void constants_shouldHaveCorrectValues() {
        assertEquals("application/pdf", MediaTypeConstants.APPLICATION_PDF);
        assertEquals("text/plain", MediaTypeConstants.TEXT_PLAIN);
        assertEquals("application/xml", MediaTypeConstants.APPLICATION_XML);
        assertEquals("application/json", MediaTypeConstants.APPLICATION_JSON);
        assertEquals("application/octet-stream", MediaTypeConstants.APPLICATION_OCTET_STREAM);
    }

    @Test
    void APPLICATION_WORD_shouldContainCorrectMimeType() {
        assertTrue(MediaTypeConstants.APPLICATION_WORD.contains("wordprocessingml"));
    }

    @Test
    void constants_shouldNotBeEmpty() {
        assertNotNull(MediaTypeConstants.APPLICATION_PDF);
        assertNotNull(MediaTypeConstants.APPLICATION_WORD);
        assertNotNull(MediaTypeConstants.TEXT_PLAIN);
        assertNotNull(MediaTypeConstants.APPLICATION_XML);
        assertNotNull(MediaTypeConstants.APPLICATION_JSON);
        assertNotNull(MediaTypeConstants.APPLICATION_OCTET_STREAM);
    }
}