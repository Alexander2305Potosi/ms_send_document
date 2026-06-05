package com.example.fileprocessor.domain.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MimeTypeUtilTest {

    @ParameterizedTest
    @CsvSource({
        "file.pdf, application/pdf",
        "FILE.PDF, application/pdf",
        "data.csv, text/csv",
        "config.xml, application/xml",
        "doc.zip, application/zip",
        "image.jpg, image/jpeg",
        "photo.jpeg, image/jpeg",
        "pic.png, image/png",
        "report.xlsx, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "letter.docx, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "unknown.ext, application/octet-stream",
        "'', application/octet-stream"
    })
    void getMimeType_variousExtensions_returnsCorrectType(String filename, String expected) {
        assertEquals(expected, MimeTypeUtil.getMimeType(filename));
    }

    @Test
    void getMimeType_nullInput_returnsOctetStream() {
        assertEquals("application/octet-stream", MimeTypeUtil.getMimeType(null));
    }
}
