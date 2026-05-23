package com.example.fileprocessor.domain.usecase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessingResultCodesTest {

    @Test
    void allCodes_areNonNull() {
        assertNotNull(ProcessingResultCodes.EMPTY_CONTENT);
        assertNotNull(ProcessingResultCodes.INVALID_BASE64);
        assertNotNull(ProcessingResultCodes.INVALID_RESPONSE);
        assertNotNull(ProcessingResultCodes.UNKNOWN_ERROR);
        assertNotNull(ProcessingResultCodes.NO_SUCURSAL);
    }

    @Test
    void codes_haveCorrectNames() {
        assertEquals("EMPTY_CONTENT", ProcessingResultCodes.EMPTY_CONTENT.name());
        assertEquals("INVALID_BASE64", ProcessingResultCodes.INVALID_BASE64.name());
        assertEquals("INVALID_RESPONSE", ProcessingResultCodes.INVALID_RESPONSE.name());
        assertEquals("UNKNOWN_ERROR", ProcessingResultCodes.UNKNOWN_ERROR.name());
        assertEquals("NO_SUCURSAL", ProcessingResultCodes.NO_SUCURSAL.name());
        assertEquals("No se encontró sucursal", ProcessingResultCodes.NO_SUCURSAL.value());
    }
}
