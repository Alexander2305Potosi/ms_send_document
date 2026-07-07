package com.example.fileprocessor.infrastructure.drivenadapters;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.BAD_GATEWAY;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILURE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.GATEWAY_TIMEOUT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SERVICE_UNAVAILABLE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SOURCE_NOT_FOUND;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SOURCE_RATE_LIMIT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.UNKNOWN_ERROR;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class AdapterErrorMapperTest {

    // ─────────────────────────────────────────────────────────────────────────
    // resolveErrorCode
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class ResolveErrorCode {

        @Test
        void withHttp404ReturnsSourceNotFound() {
            WebClientResponseException ex = WebClientResponseException.create(
                    404, "Not Found", HttpHeaders.EMPTY, new byte[0], null);

            assertEquals(SOURCE_NOT_FOUND.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withHttp429ReturnsSourceRateLimit() {
            WebClientResponseException ex = WebClientResponseException.create(
                    429, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null);

            assertEquals(SOURCE_RATE_LIMIT.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withHttp500ReturnsBadGateway() {
            WebClientResponseException ex = WebClientResponseException.create(
                    500, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null);

            assertEquals(BAD_GATEWAY.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withHttp502ReturnsBadGateway() {
            WebClientResponseException ex = WebClientResponseException.create(
                    502, "Bad Gateway", HttpHeaders.EMPTY, new byte[0], null);

            assertEquals(BAD_GATEWAY.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withHttp503ReturnsBadGateway() {
            WebClientResponseException ex = WebClientResponseException.create(
                    503, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], null);

            assertEquals(BAD_GATEWAY.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withHttp400ReturnsUnknownError() {
            WebClientResponseException ex = WebClientResponseException.create(
                    400, "Bad Request", HttpHeaders.EMPTY, new byte[0], null);

            assertEquals(UNKNOWN_ERROR.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withHttp401ReturnsUnknownError() {
            WebClientResponseException ex = WebClientResponseException.create(
                    401, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null);

            assertEquals(UNKNOWN_ERROR.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withTimeoutExceptionReturnsGatewayTimeout() {
            TimeoutException ex = new TimeoutException("Connection timed out");

            assertEquals(GATEWAY_TIMEOUT.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withMessageContainingTimeoutReturnsGatewayTimeout() {
            RuntimeException ex = new RuntimeException("Read timeout after 30s");

            assertEquals(GATEWAY_TIMEOUT.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withConnectExceptionReturnsServiceUnavailable() {
            ConnectException ex = new ConnectException("Connection refused");

            assertEquals(SERVICE_UNAVAILABLE.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withProcessingExceptionPreservesOriginalCode() {
            ProcessingException pe = new ProcessingException("Bad file", "INVALID_BASE64");

            assertEquals("INVALID_BASE64", AdapterErrorMapper.resolveErrorCode(pe));
        }

        @Test
        void withProcessingExceptionBlankCodeReturnsUnknownError() {
            ProcessingException pe = new ProcessingException("Error", "");

            assertEquals(UNKNOWN_ERROR.name(),
                    AdapterErrorMapper.resolveErrorCode(pe));
        }

        @Test
        void withProcessingExceptionNullCodeReturnsUnknownError() {
            ProcessingException pe = new ProcessingException("Error", null);

            assertEquals(UNKNOWN_ERROR.name(),
                    AdapterErrorMapper.resolveErrorCode(pe));
        }

        @Test
        void withGenericRuntimeExceptionReturnsUnknownError() {
            RuntimeException ex = new RuntimeException("Something broke");

            assertEquals(UNKNOWN_ERROR.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withNullPointerExceptionReturnsUnknownError() {
            NullPointerException ex = new NullPointerException("null reference");

            assertEquals(UNKNOWN_ERROR.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unwrap (cause chain resolution)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class UnwrapCauseChain {

        @Test
        void unwrapsToWebClientResponseExceptionInCauseChain() {
            WebClientResponseException wce = WebClientResponseException.create(
                    404, "Not Found", HttpHeaders.EMPTY, new byte[0], null);
            RuntimeException wrapper = new RuntimeException("Wrapper", wce);

            assertEquals(SOURCE_NOT_FOUND.name(),
                    AdapterErrorMapper.resolveErrorCode(wrapper));
        }

        @Test
        void unwrapsToTimeoutExceptionInCauseChain() {
            TimeoutException timeout = new TimeoutException("timed out");
            RuntimeException wrapper = new RuntimeException("Wrapper",
                    new IllegalStateException("Mid-layer", timeout));

            assertEquals(GATEWAY_TIMEOUT.name(),
                    AdapterErrorMapper.resolveErrorCode(wrapper));
        }

        @Test
        void unwrapsToConnectExceptionInCauseChain() {
            ConnectException connect = new ConnectException("Connection refused");
            RuntimeException wrapper = new RuntimeException("Wrapper", connect);

            assertEquals(SERVICE_UNAVAILABLE.name(),
                    AdapterErrorMapper.resolveErrorCode(wrapper));
        }

        @Test
        void stopsAtProcessingExceptionDoesNotContinueUnwrapping() {
            ProcessingException pe = new ProcessingException("Domain error", "PATTERN_MISMATCH",
                    new TimeoutException("This should be ignored"));

            assertEquals("PATTERN_MISMATCH", AdapterErrorMapper.resolveErrorCode(pe));
        }

        @Test
        void stopsAtWebClientResponseExceptionDoesNotContinueUnwrapping() {
            WebClientResponseException wce = WebClientResponseException.create(
                    429, "Rate Limited", HttpHeaders.EMPTY, new byte[0], null);

            // Even though the outer wrapper is a RuntimeException, the unwrap should stop at wce
            RuntimeException wrapper = new RuntimeException("Wrapper", wce);
            assertEquals(SOURCE_RATE_LIMIT.name(),
                    AdapterErrorMapper.resolveErrorCode(wrapper));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // buildErrorResponse
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class BuildErrorResponse {

        @Test
        void withHttp500BuildsResponseWithBadGateway() {
            WebClientResponseException ex = WebClientResponseException.create(
                    500, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null);

            FileUploadResponse response = AdapterErrorMapper.buildErrorResponse(ex, "trace-123");

            assertFalse(response.isSuccess());
            assertEquals(FAILURE.name(), response.getStatus());
            assertEquals(BAD_GATEWAY.name(), response.getSyncStatus());
            assertTrue(response.getMessage().contains("500"));
            assertEquals("trace-123", response.getTraceId());
            assertNotNull(response.getProcessedAt());
        }

        @Test
        void withTimeoutExceptionBuildsResponseWithGatewayTimeout() {
            TimeoutException ex = new TimeoutException("Connection timed out");

            FileUploadResponse response = AdapterErrorMapper.buildErrorResponse(ex, "trace-456");

            assertFalse(response.isSuccess());
            assertEquals(GATEWAY_TIMEOUT.name(), response.getSyncStatus());
            assertTrue(response.getMessage().contains("Timeout"));
            assertEquals("trace-456", response.getTraceId());
        }

        @Test
        void withConnectExceptionBuildsResponseWithServiceUnavailable() {
            ConnectException ex = new ConnectException("Connection refused");

            FileUploadResponse response = AdapterErrorMapper.buildErrorResponse(ex, "trace-789");

            assertFalse(response.isSuccess());
            assertEquals(SERVICE_UNAVAILABLE.name(), response.getSyncStatus());
            assertTrue(response.getMessage().contains("Connection refused"));
        }

        @Test
        void withProcessingExceptionPreservesDomainCode() {
            ProcessingException pe = new ProcessingException("File too large", "SIZE_EXCEEDED");

            FileUploadResponse response = AdapterErrorMapper.buildErrorResponse(pe, "trace-aaa");

            assertFalse(response.isSuccess());
            assertEquals("SIZE_EXCEEDED", response.getSyncStatus());
            assertEquals("File too large", response.getMessage());
        }

        @Test
        void withGenericExceptionDefaultsToUnknownError() {
            RuntimeException ex = new RuntimeException("Something unexpected");

            FileUploadResponse response = AdapterErrorMapper.buildErrorResponse(ex, null);

            assertFalse(response.isSuccess());
            assertEquals(UNKNOWN_ERROR.name(), response.getSyncStatus());
            assertEquals("Something unexpected", response.getMessage());
            assertNull(response.getTraceId());
        }

        @Test
        void withNullMessageUsesUnknownErrorDescription() {
            RuntimeException ex = new RuntimeException((String) null);

            FileUploadResponse response = AdapterErrorMapper.buildErrorResponse(ex, "trace-null");

            assertEquals(UNKNOWN_ERROR.value(), response.getMessage());
        }

        @Test
        void withWrappedSslExceptionBuildsResponseWithSslErrorMessage() {
            javax.net.ssl.SSLHandshakeException sslEx = new javax.net.ssl.SSLHandshakeException("PKIX path building failed");
            RuntimeException wrapper = new RuntimeException("Outer request failed", sslEx);

            FileUploadResponse response = AdapterErrorMapper.buildErrorResponse(wrapper, "trace-ssl");

            assertFalse(response.isSuccess());
            assertEquals(UNKNOWN_ERROR.name(), response.getSyncStatus());
            assertEquals("PKIX path building failed", response.getMessage());
            assertEquals("trace-ssl", response.getTraceId());
        }

        @Test
        void withWrappedExceptionHavingNullMessageFallsBackToOuterMessage() {
            javax.net.ssl.SSLHandshakeException sslEx = new javax.net.ssl.SSLHandshakeException(null);
            RuntimeException wrapper = new RuntimeException("Outer connection failed due to SSL handshake issue", sslEx);

            FileUploadResponse response = AdapterErrorMapper.buildErrorResponse(wrapper, "trace-fallback");

            assertFalse(response.isSuccess());
            assertEquals(UNKNOWN_ERROR.name(), response.getSyncStatus());
            assertEquals("Outer connection failed due to SSL handshake issue", response.getMessage());
            assertEquals("trace-fallback", response.getTraceId());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // toErrorResponse (reactive variant)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class ToErrorResponse {

        @Test
        void emitsFileUploadResponseWithCorrectMapping() {
            WebClientResponseException ex = WebClientResponseException.create(
                    404, "Not Found", HttpHeaders.EMPTY, new byte[0], null);

            StepVerifier.create(AdapterErrorMapper.toErrorResponse(ex, "reactive-trace"))
                    .assertNext(response -> {
                        assertFalse(response.isSuccess());
                        assertEquals(SOURCE_NOT_FOUND.name(), response.getSyncStatus());
                        assertEquals("reactive-trace", response.getTraceId());
                    })
                    .verifyComplete();
        }

        @Test
        void emitsExactlyOneElement() {
            RuntimeException ex = new RuntimeException("test error");

            StepVerifier.create(AdapterErrorMapper.toErrorResponse(ex, "trace"))
                    .expectNextCount(1)
                    .verifyComplete();
        }
    }
}
