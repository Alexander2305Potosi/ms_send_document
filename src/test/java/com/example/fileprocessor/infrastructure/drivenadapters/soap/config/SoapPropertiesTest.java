package com.example.fileprocessor.infrastructure.drivenadapters.soap.config;

import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SoapPropertiesTest {

    @Test
    void soapPropertiesRecordCreatesValidProperties() {
        SoapProperties props = new SoapProperties(
                "http://localhost:9000/soap", "SYS-01", "user", "h-ns", "b-ns", "s-ns",
                "token", "dest", "d-ns", "op", "action", "CLASS-1",
                Map.of(), Map.of(), 30, 3);

        assertEquals("http://localhost:9000/soap", props.endpoint());
        assertEquals("SYS-01", props.systemId());
        assertEquals(30, props.timeoutSeconds());
        assertEquals(3, props.retryAttempts());
    }
}
