package com.example.fileprocessor.infrastructure.drivenadapters;

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
        void withHttp404_returnsSourceNotFound() {
            WebClientResponseException ex = WebClientResponseException.create(
                    404, "Not Found", HttpHeaders.EMPTY, new byte[0], null);

            assertEquals(ProcessingResultCodes.SOURCE_NOT_FOUND.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withHttp429_returnsSourceRateLimit() {
            WebClientResponseException ex = WebClientResponseException.create(
                    429, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null);

            assertEquals(ProcessingResultCodes.SOURCE_RATE_LIMIT.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withHttp500_returnsBadGateway() {
            WebClientResponseException ex = WebClientResponseException.create(
                    500, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null);

            assertEquals(ProcessingResultCodes.BAD_GATEWAY.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withHttp502_returnsBadGateway() {
            WebClientResponseException ex = WebClientResponseException.create(
                    502, "Bad Gateway", HttpHeaders.EMPTY, new byte[0], null);

            assertEquals(ProcessingResultCodes.BAD_GATEWAY.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withHttp503_returnsBadGateway() {
            WebClientResponseException ex = WebClientResponseException.create(
                    503, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], null);

            assertEquals(ProcessingResultCodes.BAD_GATEWAY.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withHttp400_returnsUnknownError() {
            WebClientResponseException ex = WebClientResponseException.create(
                    400, "Bad Request", HttpHeaders.EMPTY, new byte[0], null);

            assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withHttp401_returnsUnknownError() {
            WebClientResponseException ex = WebClientResponseException.create(
                    401, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null);

            assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withTimeoutException_returnsGatewayTimeout() {
            TimeoutException ex = new TimeoutException("Connection timed out");

            assertEquals(ProcessingResultCodes.GATEWAY_TIMEOUT.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withMessageContainingTimeout_returnsGatewayTimeout() {
            RuntimeException ex = new RuntimeException("Read timeout after 30s");

            assertEquals(ProcessingResultCodes.GATEWAY_TIMEOUT.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withConnectException_returnsServiceUnavailable() {
            ConnectException ex = new ConnectException("Connection refused");

            assertEquals(ProcessingResultCodes.SERVICE_UNAVAILABLE.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withProcessingException_preservesOriginalCode() {
            ProcessingException pe = new ProcessingException("Bad file", "INVALID_BASE64");

            assertEquals("INVALID_BASE64", AdapterErrorMapper.resolveErrorCode(pe));
        }

        @Test
        void withProcessingExceptionBlankCode_returnsUnknownError() {
            ProcessingException pe = new ProcessingException("Error", "");

            assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(),
                    AdapterErrorMapper.resolveErrorCode(pe));
        }

        @Test
        void withProcessingExceptionNullCode_returnsUnknownError() {
            ProcessingException pe = new ProcessingException("Error", null);

            assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(),
                    AdapterErrorMapper.resolveErrorCode(pe));
        }

        @Test
        void withGenericRuntimeException_returnsUnknownError() {
            RuntimeException ex = new RuntimeException("Something broke");

            assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(),
                    AdapterErrorMapper.resolveErrorCode(ex));
        }

        @Test
        void withNullPointerException_returnsUnknownError() {
            NullPointerException ex = new NullPointerException("null reference");

            assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(),
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

            assertEquals(ProcessingResultCodes.SOURCE_NOT_FOUND.name(),
                    AdapterErrorMapper.resolveErrorCode(wrapper));
        }

        @Test
        void unwrapsToTimeoutExceptionInCauseChain() {
            TimeoutException timeout = new TimeoutException("timed out");
            RuntimeException wrapper = new RuntimeException("Wrapper",
                    new IllegalStateException("Mid-layer", timeout));

            assertEquals(ProcessingResultCodes.GATEWAY_TIMEOUT.name(),
                    AdapterErrorMapper.resolveErrorCode(wrapper));
        }

        @Test
        void unwrapsToConnectExceptionInCauseChain() {
            ConnectException connect = new ConnectException("Connection refused");
            RuntimeException wrapper = new RuntimeException("Wrapper", connect);

            assertEquals(ProcessingResultCodes.SERVICE_UNAVAILABLE.name(),
                    AdapterErrorMapper.resolveErrorCode(wrapper));
        }

        @Test
        void stopsAtProcessingException_doesNotContinueUnwrapping() {
            ProcessingException pe = new ProcessingException("Domain error", "PATTERN_MISMATCH",
                    new TimeoutException("This should be ignored"));

            assertEquals("PATTERN_MISMATCH", AdapterErrorMapper.resolveErrorCode(pe));
        }

        @Test
        void stopsAtWebClientResponseException_doesNotContinueUnwrapping() {
            WebClientResponseException wce = WebClientResponseException.create(
                    429, "Rate Limited", HttpHeaders.EMPTY, new byte[0], null);

            // Even though the outer wrapper is a RuntimeException, the unwrap should stop at wce
            RuntimeException wrapper = new RuntimeException("Wrapper", wce);
            assertEquals(ProcessingResultCodes.SOURCE_RATE_LIMIT.name(),
                    AdapterErrorMapper.resolveErrorCode(wrapper));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // buildErrorResponse
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    class BuildErrorResponse {

        @Test
        void withHttp500_buildsResponseWithBadGateway() {
            WebClientResponseException ex = WebClientResponseException.create(
                    500, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null);

            FileUploadResponse response = AdapterErrorMapper.buildErrorResponse(ex, "trace-123");

            assertFalse(response.isSuccess());
            assertEquals(ProcessingResultCodes.FAILURE.name(), response.getStatus());
            assertEquals(ProcessingResultCodes.BAD_GATEWAY.name(), response.getSyncStatus());
            assertTrue(response.getMessage().contains("500"));
            assertEquals("trace-123", response.getTraceId());
            assertNotNull(response.getProcessedAt());
        }

        @Test
        void withTimeoutException_buildsResponseWithGatewayTimeout() {
            TimeoutException ex = new TimeoutException("Connection timed out");

            FileUploadResponse response = AdapterErrorMapper.buildErrorResponse(ex, "trace-456");

            assertFalse(response.isSuccess());
            assertEquals(ProcessingResultCodes.GATEWAY_TIMEOUT.name(), response.getSyncStatus());
            assertTrue(response.getMessage().contains("Timeout"));
            assertEquals("trace-456", response.getTraceId());
        }

        @Test
        void withConnectException_buildsResponseWithServiceUnavailable() {
            ConnectException ex = new ConnectException("Connection refused");

            FileUploadResponse response = AdapterErrorMapper.buildErrorResponse(ex, "trace-789");

            assertFalse(response.isSuccess());
            assertEquals(ProcessingResultCodes.SERVICE_UNAVAILABLE.name(), response.getSyncStatus());
            assertTrue(response.getMessage().contains("Connection refused"));
        }

        @Test
        void withProcessingException_preservesDomainCode() {
            ProcessingException pe = new ProcessingException("File too large", "SIZE_EXCEEDED");

            FileUploadResponse response = AdapterErrorMapper.buildErrorResponse(pe, "trace-aaa");

            assertFalse(response.isSuccess());
            assertEquals("SIZE_EXCEEDED", response.getSyncStatus());
            assertEquals("File too large", response.getMessage());
        }

        @Test
        void withGenericException_defaultsToUnknownError() {
            RuntimeException ex = new RuntimeException("Something unexpected");

            FileUploadResponse response = AdapterErrorMapper.buildErrorResponse(ex, null);

            assertFalse(response.isSuccess());
            assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.name(), response.getSyncStatus());
            assertEquals("Something unexpected", response.getMessage());
            assertNull(response.getTraceId());
        }

        @Test
        void withNullMessage_usesUnknownErrorDescription() {
            RuntimeException ex = new RuntimeException((String) null);

            FileUploadResponse response = AdapterErrorMapper.buildErrorResponse(ex, "trace-null");

            assertEquals(ProcessingResultCodes.UNKNOWN_ERROR.value(), response.getMessage());
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
                        assertEquals(ProcessingResultCodes.SOURCE_NOT_FOUND.name(), response.getSyncStatus());
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
