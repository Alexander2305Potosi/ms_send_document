package com.example.fileprocessor.domain.util;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.EMPTY_CONTENT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.INVALID_BASE64;

import com.example.fileprocessor.domain.exception.InvalidBase64Exception;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class Base64UtilsTest {

    @Test
    void decodeSafeWithValidBase64ReturnsDecodedBytes() {
        String original = "Hello World";
        String encoded = java.util.Base64.getEncoder().encodeToString(original.getBytes());
        byte[] decoded = Base64Utils.decodeSafe(encoded, "file.txt", "doc-123");
        assertEquals(original, new String(decoded));
    }

    @Test
    void decodeSafeWithNullThrowsInvalidBase64Exception() {
        InvalidBase64Exception exception = assertThrows(InvalidBase64Exception.class, () -> {
            Base64Utils.decodeSafe(null, "file.txt", "doc-123");
        });
        assertEquals(EMPTY_CONTENT.name(), exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Empty Base64 content"));
    }

    @Test
    void decodeSafeWithBlankThrowsInvalidBase64Exception() {
        InvalidBase64Exception exception = assertThrows(InvalidBase64Exception.class, () -> {
            Base64Utils.decodeSafe("   ", "file.txt", "doc-123");
        });
        assertEquals(EMPTY_CONTENT.name(), exception.getErrorCode());
    }

    @Test
    void decodeSafeWithInvalidBase64ThrowsInvalidBase64Exception() {
        InvalidBase64Exception exception = assertThrows(InvalidBase64Exception.class, () -> {
            Base64Utils.decodeSafe("invalid base64 content @@@", "file.txt", "doc-123");
        });
        assertEquals(INVALID_BASE64.name(), exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Invalid Base64 content"));
        assertNotNull(exception.getCause());
    }

    @Test
    void encodeWithNullReturnsEmptyString() {
        assertEquals("", Base64Utils.encode(null));
    }

    @Test
    void encodeWithValidBytesReturnsBase64String() {
        String original = "Hello";
        String expected = java.util.Base64.getEncoder().encodeToString(original.getBytes());
        assertEquals(expected, Base64Utils.encode(original.getBytes()));
    }

    @Test
    void privateConstructorCanBeCalledViaReflection() throws Exception {
        Constructor<Base64Utils> constructor = Base64Utils.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        Base64Utils instance = constructor.newInstance();
        assertNotNull(instance);
    }
}
