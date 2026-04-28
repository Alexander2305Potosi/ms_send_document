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
        settings.setMaxFilenameLength(255);
        settings.setMaxFileSizeMb(50);
        settings.setFoldersToSkip(java.util.List.of());
        settings.setKeywords(java.util.List.of());
        settings.setOriginPatternsToSend(java.util.List.of());

        assertEquals(10485760L, settings.maxSize());
        assertEquals("pdf,txt,csv", settings.allowedTypes());
        assertEquals(255, settings.maxFilenameLength());
        assertEquals(50, settings.maxFileSizeMb());
        assertTrue(settings.foldersToSkip().isEmpty());
        assertTrue(settings.keywords().isEmpty());
        assertTrue(settings.originPatternsToSend().isEmpty());
    }

    @Test
    void s3Settings_shouldBindValues() {
        ProcessorSettings settings = new ProcessorSettings();
        settings.setMaxSize(52428800L);
        settings.setAllowedTypes("pdf,txt,csv,zip");
        settings.setMaxFilenameLength(255);
        settings.setMaxFileSizeMb(100);
        settings.setFoldersToSkip(java.util.List.of("/tmp"));
        settings.setKeywords(java.util.List.of("test", "mock", "archive"));
        settings.setOriginPatternsToSend(java.util.List.of("incoming", "documents", "archive"));

        assertEquals(52428800L, settings.maxSize());
        assertEquals("pdf,txt,csv,zip", settings.allowedTypes());
        assertEquals(255, settings.maxFilenameLength());
        assertEquals(100, settings.maxFileSizeMb());
        assertTrue(settings.foldersToSkip().contains("/tmp"));
        assertTrue(settings.keywords().contains("test"));
        assertTrue(settings.originPatternsToSend().contains("archive"));
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
