package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentStatusTest {

    @Test
    void success_hasCorrectValue() {
        assertEquals("SUCCESS", DocumentStatus.SUCCESS.name());
    }

    @Test
    void failure_hasCorrectValue() {
        assertEquals("FAILURE", DocumentStatus.FAILURE.name());
    }
}
