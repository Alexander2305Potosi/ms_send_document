package com.example.fileprocessor.domain.usecase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoapUseCaseConstantsTest {

    @Test
    void IMPL_NAME_shouldBeSOAP() {
        assertEquals("SOAP", SoapUseCaseConstants.IMPL_NAME);
    }
}