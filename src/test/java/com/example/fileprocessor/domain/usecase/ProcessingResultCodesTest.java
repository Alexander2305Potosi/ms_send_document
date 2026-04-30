package com.example.fileprocessor.infrastructure.entrypoints.rest.handler;

import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessingResultCodesTest {

    @Test
    void allCodes_areNonNull() {
        assertNotNull(ProcessingResultCodes.GATEWAY_TIMEOUT);
        assertNotNull(ProcessingResultCodes.BAD_GATEWAY);
        assertNotNull(ProcessingResultCodes.CLIENT_ERROR);
        assertNotNull(ProcessingResultCodes.UNKNOWN_ERROR);
        assertNotNull(ProcessingResultCodes.INVALID_BASE64);
        assertNotNull(ProcessingResultCodes.SERVICE_UNAVAILABLE_ERROR);
        assertNotNull(ProcessingResultCodes.ACCESS_DENIED_ERROR);
        assertNotNull(ProcessingResultCodes.NOT_FOUND_ERROR);
    }

    @Test
    void codes_areNonEmpty() {
        assertFalse(ProcessingResultCodes.GATEWAY_TIMEOUT.isBlank());
        assertFalse(ProcessingResultCodes.BAD_GATEWAY.isBlank());
        assertFalse(ProcessingResultCodes.CLIENT_ERROR.isBlank());
        assertFalse(ProcessingResultCodes.UNKNOWN_ERROR.isBlank());
        assertFalse(ProcessingResultCodes.INVALID_BASE64.isBlank());
    }

    @Test
    void gatewayTimeout_isCorrectValue() {
        assertEquals("GATEWAY_TIMEOUT", ProcessingResultCodes.GATEWAY_TIMEOUT);
    }

    @Test
    void badGateway_isCorrectValue() {
        assertEquals("BAD_GATEWAY", ProcessingResultCodes.BAD_GATEWAY);
    }

    @Test
    void serviceUnavailable_isCorrectValue() {
        assertEquals("SERVICE_UNAVAILABLE_ERROR", ProcessingResultCodes.SERVICE_UNAVAILABLE_ERROR);
    }

    @Test
    void accessDenied_isCorrectValue() {
        assertEquals("ACCESS_DENIED_ERROR", ProcessingResultCodes.ACCESS_DENIED_ERROR);
    }
}
