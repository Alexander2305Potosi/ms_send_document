package com.example.fileprocessor.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessorsPropertiesTest {

    @Test
    void processorConfig_withBothValues() {
        ProcessorsProperties.ProcessorConfig config = new ProcessorsProperties.ProcessorConfig(
            52428800L, ".*\\.pdf$"
        );

        assertEquals(52428800L, config.maxFileSizeBytes());
        assertEquals(".*\\.pdf$", config.filenamePattern());
    }

    @Test
    void processorConfig_withNullValues() {
        ProcessorsProperties.ProcessorConfig config = new ProcessorsProperties.ProcessorConfig(
            null, null
        );

        assertNull(config.maxFileSizeBytes());
        assertNull(config.filenamePattern());
    }

    @Test
    void processorConfig_withOnlySize() {
        ProcessorsProperties.ProcessorConfig config = new ProcessorsProperties.ProcessorConfig(
            1000L, null
        );

        assertEquals(1000L, config.maxFileSizeBytes());
        assertNull(config.filenamePattern());
    }

    @Test
    void processorConfig_withOnlyPattern() {
        ProcessorsProperties.ProcessorConfig config = new ProcessorsProperties.ProcessorConfig(
            null, ".*\\.csv$"
        );

        assertNull(config.maxFileSizeBytes());
        assertEquals(".*\\.csv$", config.filenamePattern());
    }

    @Test
    void properties_withBothProcessors() {
        ProcessorsProperties.ProcessorConfig s3 = new ProcessorsProperties.ProcessorConfig(52428800L, ".*");
        ProcessorsProperties.ProcessorConfig soap = new ProcessorsProperties.ProcessorConfig(10485760L, ".*\\.pdf$");

        ProcessorsProperties props = new ProcessorsProperties(s3, soap);

        assertEquals(52428800L, props.s3().maxFileSizeBytes());
        assertEquals(10485760L, props.soap().maxFileSizeBytes());
    }
}
