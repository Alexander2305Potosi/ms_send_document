package com.example.fileprocessor.domain.usecase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class S3UseCaseConstantsTest {

    @Test
    void IMPL_NAME_shouldBeS3() {
        assertEquals("S3", S3UseCaseConstants.IMPL_NAME);
    }

    @Test
    void MSG_UPLOAD_SUCCESS_shouldNotBeEmpty() {
        assertNotNull(S3UseCaseConstants.MSG_UPLOAD_SUCCESS);
        assertTrue(S3UseCaseConstants.MSG_UPLOAD_SUCCESS.contains("S3"));
    }

    @Test
    void MSG_UPLOAD_FAILURE_shouldNotBeEmpty() {
        assertNotNull(S3UseCaseConstants.MSG_UPLOAD_FAILURE);
        assertTrue(S3UseCaseConstants.MSG_UPLOAD_FAILURE.contains("failed"));
    }
}