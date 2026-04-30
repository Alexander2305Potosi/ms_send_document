package com.example.fileprocessor.domain.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Base64UtilsTest {

    @Test
    void encode_shouldEncodeBytesToBase64() {
        byte[] input = "Hello World".getBytes();
        String encoded = Base64Utils.encode(input);

        assertNotNull(encoded);
        assertEquals("SGVsbG8gV29ybGQ=", encoded);
    }

    @Test
    void decode_shouldDecodeBase64ToBytes() {
        String encoded = "SGVsbG8gV29ybGQ=";
        byte[] decoded = Base64Utils.decode(encoded);

        assertArrayEquals("Hello World".getBytes(), decoded);
    }

    @Test
    void encodeDecode_shouldBeReversible() {
        byte[] original = new byte[]{1, 2, 3, 4, 5};

        String encoded = Base64Utils.encode(original);
        byte[] decoded = Base64Utils.decode(encoded);

        assertArrayEquals(original, decoded);
    }

    @Test
    void encode_emptyArray_shouldReturnEmpty() {
        byte[] empty = new byte[]{};
        String encoded = Base64Utils.encode(empty);

        assertEquals("", encoded);
    }

    @Test
    void decode_emptyString_shouldReturnNull() {
        byte[] decoded = Base64Utils.decode("");

        assertNull(decoded);
    }
}