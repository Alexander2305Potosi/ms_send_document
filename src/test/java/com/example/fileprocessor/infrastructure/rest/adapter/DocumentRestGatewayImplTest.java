package com.example.fileprocessor.infrastructure.rest.adapter;

import com.example.fileprocessor.infrastructure.rest.config.DocumentRestProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic unit tests for DocumentRestGatewayImpl.
 * Full integration testing requires a mock HTTP server.
 */
@ExtendWith(MockitoExtension.class)
class DocumentRestGatewayImplTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Test
    void constructor_shouldAcceptValidDependencies() {
        DocumentRestProperties properties = new DocumentRestProperties(
            "http://localhost:3001",
            "/api/documents",
            "/api/document",
            30
        );

        // Verify that properties are correctly set
        assertEquals("http://localhost:3001", properties.endpoint());
        assertEquals("/api/documents", properties.listPath());
        assertEquals("/api/document", properties.getPath());
        assertEquals(30, properties.timeoutSeconds());
    }

    @Test
    void properties_shouldUseDefaultsForMissingValues() {
        DocumentRestProperties properties = new DocumentRestProperties(null, null, null, 0);

        assertEquals("http://localhost:3001", properties.endpoint());
        assertEquals("/api/documents", properties.listPath());
        assertEquals("/api/document", properties.getPath());
        assertEquals(30, properties.timeoutSeconds());
    }
}
