package com.example.fileprocessor.domain.util;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import static org.junit.jupiter.api.Assertions.*;

class MimeTypeUtilTest {

    @Test
    void getMimeTypeWithNullOrBlankReturnsOctetStream() {
        assertEquals("application/octet-stream", MimeTypeUtil.getMimeType(null));
        assertEquals("application/octet-stream", MimeTypeUtil.getMimeType(""));
        assertEquals("application/octet-stream", MimeTypeUtil.getMimeType("   "));
    }

    @Test
    void getMimeTypeWithVariousExtensionsReturnsCorrectMime() {
        assertEquals("application/pdf", MimeTypeUtil.getMimeType("test.pdf"));
        assertEquals("application/pdf", MimeTypeUtil.getMimeType("TEST.PDF"));
        assertEquals("text/csv", MimeTypeUtil.getMimeType("test.csv"));
        assertEquals("application/xml", MimeTypeUtil.getMimeType("test.xml"));
        assertEquals("application/json", MimeTypeUtil.getMimeType("test.json"));
        assertEquals("text/plain", MimeTypeUtil.getMimeType("test.txt"));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", MimeTypeUtil.getMimeType("test.docx"));
        assertEquals("application/msword", MimeTypeUtil.getMimeType("test.doc"));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", MimeTypeUtil.getMimeType("test.xlsx"));
        assertEquals("application/vnd.ms-excel", MimeTypeUtil.getMimeType("test.xls"));
        assertEquals("application/zip", MimeTypeUtil.getMimeType("test.zip"));
        assertEquals("image/png", MimeTypeUtil.getMimeType("test.png"));
        assertEquals("image/jpeg", MimeTypeUtil.getMimeType("test.jpg"));
        assertEquals("image/jpeg", MimeTypeUtil.getMimeType("test.jpeg"));
    }

    @Test
    void getMimeTypeWithUnknownExtensionReturnsOctetStream() {
        assertEquals("application/octet-stream", MimeTypeUtil.getMimeType("test.unknown"));
    }

    @Test
    void privateConstructorCanBeCalledViaReflection() throws Exception {
        Constructor<MimeTypeUtil> constructor = MimeTypeUtil.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        MimeTypeUtil instance = constructor.newInstance();
        assertNotNull(instance);
    }
}
