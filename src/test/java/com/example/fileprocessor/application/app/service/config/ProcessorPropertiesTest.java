package com.example.fileprocessor.application.app.service.config;

import com.example.fileprocessor.infrastructure.helpers.config.SoapProcessorProperties;
import com.example.fileprocessor.infrastructure.helpers.config.S3ProcessorProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessorPropertiesTest {

    @Test
    void soapProcessorProperties_shouldHaveDefaults() {
        SoapProcessorProperties props = new SoapProcessorProperties();

        assertEquals(10485760L, props.maxSize());
        assertEquals("pdf,txt,csv", props.allowedTypes());
        assertEquals(255, props.maxFilenameLength());
        assertEquals(50, props.maxFileSizeMb());
        assertTrue(props.foldersToSkip().isEmpty());
        assertTrue(props.keywords().isEmpty());
        assertTrue(props.originPatternsToSend().isEmpty());
    }

    @Test
    void s3ProcessorProperties_shouldHaveDefaults() {
        S3ProcessorProperties props = new S3ProcessorProperties();

        assertEquals(52428800L, props.maxSize());
        assertEquals("pdf,txt,csv,zip", props.allowedTypes());
        assertEquals(255, props.maxFilenameLength());
        assertEquals(100, props.maxFileSizeMb());
        assertTrue(props.foldersToSkip().contains("/tmp"));
        assertTrue(props.keywords().contains("test"));
        assertTrue(props.originPatternsToSend().contains("archive"));
    }
}