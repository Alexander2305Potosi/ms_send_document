package com.example.fileprocessor.infrastructure.drivenadapters.soap.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoapPropertiesTest {

    @Test
    void soapProperties_recordCreatesValidProperties() {
        SoapProperties props = new SoapProperties(
            "http://localhost:9000/soap",
            30
        );

        assertEquals("http://localhost:9000/soap", props.endpoint());
        assertEquals(30, props.timeoutSeconds());
    }

    @Test
    void soapProperties_withValidValues() {
        SoapProperties props = new SoapProperties(
            "http://soap.example.com/service",
            60
        );

        assertEquals("http://soap.example.com/service", props.endpoint());
        assertEquals(60, props.timeoutSeconds());
    }
}
