package com.example.fileprocessor.domain.util;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import static org.junit.jupiter.api.Assertions.*;

class ExceptionUtilsTest {

    @Test
    void getStackTraceAsStringWithNullReturnsEmptyString() {
        assertEquals("", ExceptionUtils.getStackTraceAsString(null));
    }

    @Test
    void getStackTraceAsStringWithThrowableReturnsStackTrace() {
        RuntimeException ex = new RuntimeException("test error message");
        String stackTrace = ExceptionUtils.getStackTraceAsString(ex);
        assertNotNull(stackTrace);
        assertTrue(stackTrace.contains("java.lang.RuntimeException: test error message"));
        assertTrue(stackTrace.contains("ExceptionUtilsTest"));
    }

    @Test
    void privateConstructorCanBeCalledViaReflection() throws Exception {
        Constructor<ExceptionUtils> constructor = ExceptionUtils.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        ExceptionUtils instance = constructor.newInstance();
        assertNotNull(instance);
    }
}
