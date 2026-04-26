package com.example.fileprocessor.infrastructure.entrypoints.rest.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RestApiConstantsTest {

    @Test
    void processorConstants_shouldHaveCorrectValues() {
        assertEquals("soap", RestApiConstants.PROCESSOR_SOAP);
        assertEquals("s3", RestApiConstants.PROCESSOR_S3);
    }

    @Test
    void paramProcessor_shouldBeCorrect() {
        assertEquals("processor", RestApiConstants.PARAM_PROCESSOR);
    }

    @Test
    void errorMessages_shouldNotBeEmpty() {
        assertNotNull(RestApiConstants.MSG_S3_NOT_AVAILABLE);
        assertFalse(RestApiConstants.MSG_S3_NOT_AVAILABLE.isEmpty());
        assertNotNull(RestApiConstants.MSG_UNKNOWN_PROCESSOR);
        assertFalse(RestApiConstants.MSG_UNKNOWN_PROCESSOR.isEmpty());
    }

    @Test
    void headerTraceId_shouldHaveCorrectValue() {
        assertEquals("X-Trace-Id", RestApiConstants.HEADER_TRACE_ID);
    }

    @Test
    void mdcTraceId_shouldHaveCorrectValue() {
        assertEquals("traceId", RestApiConstants.MDC_TRACE_ID);
    }

    @Test
    void operationConstants_shouldNotBeEmpty() {
        assertEquals("LOAD", RestApiConstants.OPERATION_LOAD);
        assertEquals("PROCESS", RestApiConstants.OPERATION_PROCESS);
    }

    @Test
    void statusConstants_shouldNotBeEmpty() {
        assertEquals("LOADING", RestApiConstants.STATUS_LOADING);
        assertEquals("PROCESSING", RestApiConstants.STATUS_PROCESSING);
        assertEquals("COMPLETED", RestApiConstants.STATUS_COMPLETED);
        assertEquals("FAILED", RestApiConstants.STATUS_FAILED);
    }

    @Test
    void messageConstants_shouldNotBeEmpty() {
        assertNotNull(RestApiConstants.MSG_LOADING);
        assertNotNull(RestApiConstants.MSG_PROCESSING);
        assertNotNull(RestApiConstants.MSG_NOT_FOUND);
        assertFalse(RestApiConstants.MSG_LOADING.isEmpty());
        assertFalse(RestApiConstants.MSG_PROCESSING.isEmpty());
    }
}