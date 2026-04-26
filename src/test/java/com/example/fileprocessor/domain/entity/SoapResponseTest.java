package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SoapResponseTest {

    @Test
    void builder_shouldCreateInstance() {
        Instant now = Instant.now();

        SoapResponse response = SoapResponse.builder()
            .status("SUCCESS")
            .message("File uploaded")
            .correlationId("corr-123")
            .traceId("trace-456")
            .processedAt(now)
            .externalReference("file-1")
            .build();

        assertEquals("SUCCESS", response.getStatus());
        assertEquals("File uploaded", response.getMessage());
        assertEquals("corr-123", response.getCorrelationId());
        assertEquals("trace-456", response.getTraceId());
        assertEquals(now, response.getProcessedAt());
        assertEquals("file-1", response.getExternalReference());
    }

    @Test
    void isSuccess_withSuccessStatus_shouldReturnTrue() {
        SoapResponse response = SoapResponse.builder()
            .status("SUCCESS")
            .build();

        assertTrue(response.isSuccess());
    }

    @Test
    void isSuccess_withOkStatus_shouldReturnTrue() {
        SoapResponse response = SoapResponse.builder()
            .status("OK")
            .build();

        assertTrue(response.isSuccess());
    }

    @Test
    void isSuccess_withFailureStatus_shouldReturnFalse() {
        SoapResponse response = SoapResponse.builder()
            .status("FAILURE")
            .build();

        assertFalse(response.isSuccess());
    }

    @Test
    void isSuccess_withLowercaseSuccess_shouldReturnTrue() {
        SoapResponse response = SoapResponse.builder()
            .status("success")
            .build();

        assertTrue(response.isSuccess());
    }
}