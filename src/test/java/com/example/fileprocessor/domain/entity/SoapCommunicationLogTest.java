package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SoapCommunicationLogTest {

    @Test
    void builder_shouldCreateInstance() {
        Instant now = Instant.now();

        SoapCommunicationLog log = SoapCommunicationLog.builder()
            .traceId("trace-1")
            .documentId("doc-1")
            .status("SUCCESS")
            .retryCount(0)
            .filename("test.pdf")
            .createdAt(now)
            .build();

        assertEquals("trace-1", log.getTraceId());
        assertEquals("doc-1", log.getDocumentId());
        assertEquals("SUCCESS", log.getStatus());
        assertEquals(0, log.getRetryCount());
        assertEquals("test.pdf", log.getFilename());
        assertEquals(now, log.getCreatedAt());
    }

    @Test
    void builder_withError_shouldIncludeErrorCode() {
        SoapCommunicationLog log = SoapCommunicationLog.builder()
            .traceId("trace-2")
            .documentId("doc-2")
            .status("FAILURE")
            .retryCount(2)
            .errorCode("TIMEOUT")
            .filename("large.pdf")
            .createdAt(Instant.now())
            .build();

        assertEquals("FAILURE", log.getStatus());
        assertEquals(2, log.getRetryCount());
        assertEquals("TIMEOUT", log.getErrorCode());
    }
}