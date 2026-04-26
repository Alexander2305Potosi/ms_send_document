package com.example.fileprocessor.domain.usecase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoadProductsConstantsTest {

    @Test
    void MSG_PRODUCT_LOADED_shouldNotBeEmpty() {
        assertNotNull(LoadProductsConstants.MSG_PRODUCT_LOADED);
        assertTrue(LoadProductsConstants.MSG_PRODUCT_LOADED.contains("loaded"));
    }
}