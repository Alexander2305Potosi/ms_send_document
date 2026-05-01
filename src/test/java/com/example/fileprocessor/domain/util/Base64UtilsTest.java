package com.example.fileprocessor.domain.util;

import com.example.fileprocessor.domain.exception.InvalidBase64Exception;
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
    void encodeDecode_shouldBeReversible() {
        byte[] original = new byte[]{1, 2, 3, 4, 5};

        String encoded = Base64Utils.encode(original);
        byte[] decoded = Base64Utils.decodeSafe(encoded, "test.txt", "doc-1");

        assertArrayEquals(original, decoded);
    }

    @Test
    void encode_emptyArray_shouldReturnEmpty() {
        byte[] empty = new byte[]{};
        String encoded = Base64Utils.encode(empty);

        assertEquals("", encoded);
    }

    @Test
    void decodeSafe_emptyString_shouldThrow() {
        assertThrows(InvalidBase64Exception.class,
            () -> Base64Utils.decodeSafe("", "test.txt", "doc-1"));
    }

    @Test
    void decodeSafe_nullString_shouldThrow() {
        assertThrows(InvalidBase64Exception.class,
            () -> Base64Utils.decodeSafe(null, "test.txt", "doc-1"));
    }

    @Test
    void decodeSafe_invalidBase64_shouldThrow() {
        assertThrows(InvalidBase64Exception.class,
            () -> Base64Utils.decodeSafe("not-valid!!!base64", "test.txt", "doc-1"));
    }
}
