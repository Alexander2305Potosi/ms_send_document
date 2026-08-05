package com.example.fileprocessor.domain.usecase;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.EMPTY_CONTENT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.INVALID_BASE64;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.INVALID_RESPONSE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.NO_SUCURSAL;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.UNKNOWN_ERROR;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProcessingResultCodesTest {

    @Test
    void allCodesAreNonNull() {
        assertNotNull(EMPTY_CONTENT);
        assertNotNull(INVALID_BASE64);
        assertNotNull(INVALID_RESPONSE);
        assertNotNull(UNKNOWN_ERROR);
        assertNotNull(NO_SUCURSAL);
    }

    @Test
    void codesHaveCorrectNames() {
        assertEquals("EMPTY_CONTENT", EMPTY_CONTENT.name());
        assertEquals("INVALID_BASE64", INVALID_BASE64.name());
        assertEquals("INVALID_RESPONSE", INVALID_RESPONSE.name());
        assertEquals("UNKNOWN_ERROR", UNKNOWN_ERROR.name());
        assertEquals("NO_SUCURSAL", NO_SUCURSAL.name());
        assertEquals("No se encontró sucursal", NO_SUCURSAL.value());
    }
}
