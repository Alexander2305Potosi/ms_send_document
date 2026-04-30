package com.example.fileprocessor.infrastructure.drivenadapters.soap.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoapPropertiesTest {

    @Test
    void soapProperties_recordCreatesValidProperties() {
        SoapProperties props = new SoapProperties(
            "http://localhost:9000/soap",
            30,
            3,
            1000,
            500
        );

        assertEquals("http://localhost:9000/soap", props.endpoint());
        assertEquals(30, props.timeoutSeconds());
        assertEquals(3, props.retryAttempts());
        assertEquals(1000, props.retryBackoffMillis());
        assertEquals(500, props.maxErrorBodyLength());
    }

    @Test
    void soapProperties_enforcesMinMaxErrorBodyLength() {
        SoapProperties props = new SoapProperties(
            "http://localhost:9000",
            30,
            3,
            500,
            50  // below minimum
        );

        // Constructor enforces min value of 500
        assertEquals(500, props.maxErrorBodyLength());
    }

    @Test
    void soapProperties_withValidValues() {
        SoapProperties props = new SoapProperties(
            "http://soap.example.com/service",
            60,
            5,
            2000,
            1000
        );

        assertEquals("http://soap.example.com/service", props.endpoint());
        assertEquals(60, props.timeoutSeconds());
        assertEquals(5, props.retryAttempts());
        assertEquals(2000, props.retryBackoffMillis());
        assertEquals(1000, props.maxErrorBodyLength());
    }
}
