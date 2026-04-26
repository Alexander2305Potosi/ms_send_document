package com.example.fileprocessor.infrastructure.drivenadapters.aws.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AwsConfigConstantsTest {

    @Test
    void DUMMY_ACCESS_KEY_shouldHaveCorrectValue() {
        assertEquals("dummy", AwsConfigConstants.DUMMY_ACCESS_KEY);
    }

    @Test
    void DUMMY_SECRET_KEY_shouldHaveCorrectValue() {
        assertEquals("dummy", AwsConfigConstants.DUMMY_SECRET_KEY);
    }

    @Test
    void constants_shouldNotBeEmpty() {
        assertNotNull(AwsConfigConstants.DUMMY_ACCESS_KEY);
        assertNotNull(AwsConfigConstants.DUMMY_SECRET_KEY);
        assertFalse(AwsConfigConstants.DUMMY_ACCESS_KEY.isEmpty());
        assertFalse(AwsConfigConstants.DUMMY_SECRET_KEY.isEmpty());
    }
}