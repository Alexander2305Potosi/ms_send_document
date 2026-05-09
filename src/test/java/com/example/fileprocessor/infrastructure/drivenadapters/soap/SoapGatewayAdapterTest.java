package com.example.fileprocessor.infrastructure.drivenadapters.soap;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.mapper.SoapMapper;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SoapGatewayAdapterTest {

    @Mock
    private WebClient soapWebClient;

    @Mock
    private SoapMapper mapper;

    @Mock
    private WebClient.RequestBodyUriSpec bodyUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec headersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private SoapProperties properties;
    private SoapGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new SoapProperties(
            "http://localhost:8080/soap", "SYS-01", "user", "h-ns", "b-ns",
            null, null, null, null, "action", List.of(), Map.of(), Map.of(), 30, 1
        );
        adapter = new SoapGatewayAdapter(soapWebClient, properties, mapper);
    }

    private void mockWebClientSuccess(String responseBody) {
        when(soapWebClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.contentType(any())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.header(anyString(), anyString())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(responseBody));
    }

    @Test
    void send_whenSuccessful_returnsSuccessResult() {
        when(mapper.buildEnvelope(any(), any(), anyString())).thenReturn("<soap>request</soap>");
        when(mapper.parseResponse(anyString(), anyString())).thenReturn(
            ExternalServiceResponse.builder()
                .status("OK")
                .correlationId("corr-123")
                .processedAt(Instant.now())
                .build()
        );
        mockWebClientSuccess("<soap>response</soap>");

        StepVerifier.create(adapter.send(FileUploadRequest.builder()
                .filename("test.pdf")
                .content(new byte[]{1})
                .build())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .assertNext(result -> {
                assertTrue(result.isSuccess());
                assertEquals("corr-123", result.getCorrelationId());
            })
            .verifyComplete();
    }

    @Test
    void send_whenHttp500_returnsFailureResult() {
        when(mapper.buildEnvelope(any(), any(), anyString())).thenReturn("<soap>request</soap>");
        
        when(soapWebClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.contentType(any())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.header(anyString(), anyString())).thenReturn(bodyUriSpec);
        when(bodyUriSpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        
        WebClientResponseException ex = mock(WebClientResponseException.class);
        when(ex.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
        when(ex.getMessage()).thenReturn("Server error");
        
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.error(ex));

        StepVerifier.create(adapter.send(FileUploadRequest.builder().filename("f.pdf").build())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(DocumentStatus.FAILURE.name(), result.getStatus());
            })
            .verifyComplete();
    }
}
