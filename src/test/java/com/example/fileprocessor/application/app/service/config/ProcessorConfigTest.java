package com.example.fileprocessor.application.app.service.config;

import com.example.fileprocessor.infrastructure.helpers.config.ProcessorConfig;
import com.example.fileprocessor.infrastructure.helpers.config.ProcessorSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessorConfigTest {

    @Test
    void soapSettings_shouldBindValues() {
        ProcessorSettings settings = new ProcessorSettings();
        settings.setMaxSize(10485760L);
        settings.setAllowedTypes("pdf,txt,csv");
        settings.setKeywords(java.util.List.of());

        assertEquals(10485760L, settings.maxSize());
        assertEquals("pdf,txt,csv", settings.allowedTypes());
        assertTrue(settings.keywords().isEmpty());
    }

    @Test
    void s3Settings_shouldBindValues() {
        ProcessorSettings settings = new ProcessorSettings();
        settings.setMaxSize(52428800L);
        settings.setAllowedTypes("pdf,txt,csv,zip");
        settings.setKeywords(java.util.List.of("test", "mock", "archive"));

        assertEquals(52428800L, settings.maxSize());
        assertEquals("pdf,txt,csv,zip", settings.allowedTypes());
        assertTrue(settings.keywords().contains("test"));
        assertTrue(settings.keywords().contains("archive"));
    }

    @Test
    void processorConfig_shouldHoldBothSettings() {
        ProcessorSettings soap = new ProcessorSettings();
        soap.setAllowedTypes("pdf");
        ProcessorSettings s3 = new ProcessorSettings();
        s3.setAllowedTypes("zip");

        ProcessorConfig config = new ProcessorConfig();
        config.setSoap(soap);
        config.setS3(s3);

        assertNotNull(config.getSoap());
        assertNotNull(config.getS3());
        assertEquals("pdf", config.getSoap().allowedTypes());
        assertEquals("zip", config.getS3().allowedTypes());
    }

    @Test
    void forProcessor_shouldReturnCorrectSettings() {
        ProcessorSettings soap = new ProcessorSettings();
        soap.setAllowedTypes("pdf");
        ProcessorSettings s3 = new ProcessorSettings();
        s3.setAllowedTypes("zip");

        ProcessorConfig config = new ProcessorConfig();
        config.setSoap(soap);
        config.setS3(s3);

        assertEquals("zip", config.forProcessor("s3").allowedTypes());
        assertEquals("pdf", config.forProcessor("SOAP").allowedTypes());
        assertEquals("pdf", config.forProcessor("unknown").allowedTypes());
    }
}