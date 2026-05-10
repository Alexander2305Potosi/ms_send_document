package com.example.fileprocessor.infrastructure.drivenadapters.aws;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.drivenadapters.aws.config.S3Properties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import reactor.util.context.Context;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3GatewayAdapterTest {

    @Mock
    private S3AsyncClient s3Client;

    private S3Properties s3Properties;
    private S3GatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        s3Properties = new S3Properties(
            "test-bucket", "us-east-1", null, true,
            null, null, 1, 500, 10, "docs/"
        );
        adapter = new S3GatewayAdapter(s3Client, s3Properties);
    }

    private static FileUploadRequest request(String docId, String filename, byte[] content) {
        return FileUploadRequest.builder()
            .documentId(docId)
            .filename(filename)
            .contentType("application/pdf")
            .content(content)
            .build();
    }

    private static FileUploadRequest validRequest() {
        return request("doc-1", "test.pdf", new byte[]{1, 2, 3});
    }

    @Test
    void sanitizeFilename_withNull_returnsUnnamed() {
        assertEquals("unnamed", adapter.sanitizeFilename(null));
    }

    @Test
    void sanitizeFilename_withBlank_returnsUnnamed() {
        assertEquals("unnamed", adapter.sanitizeFilename("   "));
    }

    @Test
    void sanitizeFilename_withNormalFilename_passesThrough() {
        assertEquals("test.pdf", adapter.sanitizeFilename("test.pdf"));
    }

    @Test
    void buildKey_constructsCorrectS3Key() {
        s3Properties = new S3Properties("bucket", "us-east-1", null, true, null, null, 1, 500, 10, "uploads/");
        adapter = new S3GatewayAdapter(s3Client, s3Properties);
        assertEquals("uploads/trace-123/doc.pdf", adapter.buildKey("trace-123", "doc.pdf"));
    }

    @Test
    void isRetryableException_withTimeoutException_returnsTrue() {
        assertTrue(adapter.isRetryableException(new TimeoutException()));
    }

    @Test
    void categorizeS3Error_with403StatusCode_returnsAccessDenied() {
        S3Exception ex = mock(S3Exception.class);
        when(ex.statusCode()).thenReturn(403);
        assertEquals(ProcessingResultCodes.DEST_UNAUTHORIZED.name(), adapter.categorizeS3Error(ex));
    }

    @Test
    void send_withNullContent_returnsEmptyContentResult() {
        FileUploadRequest req = request("doc-1", "test.pdf", null);

        StepVerifier.create(adapter.send(req)
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(ProcessingResultCodes.FAILURE.name(), result.getStatus());
                assertEquals(ProcessingResultCodes.EMPTY_CONTENT.name(), result.getErrorCode());
            })
            .verifyComplete();

        verifyNoInteractions(s3Client);
    }

    @Test
    void send_whenSuccessful_returnsSuccessResult() {
        PutObjectResponse response = PutObjectResponse.builder().eTag("etag-abc123").build();
        CompletableFuture<PutObjectResponse> future = CompletableFuture.completedFuture(response);
        when(s3Client.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
            .thenReturn(future);

        StepVerifier.create(adapter.send(validRequest())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .assertNext(result -> {
                assertTrue(result.isSuccess());
                assertEquals(ProcessingResultCodes.SUCCESS.name(), result.getStatus());
                assertEquals("etag-abc123", result.getCorrelationId());
            })
            .verifyComplete();
    }

    @Test
    void send_whenS3Exception403_returnsAccessDenied() {
        S3Exception s3ex = (S3Exception) S3Exception.builder()
            .message("Forbidden")
            .statusCode(403)
            .build();
            
        CompletableFuture<PutObjectResponse> future = new CompletableFuture<>();
        future.completeExceptionally(s3ex);
        when(s3Client.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
            .thenReturn(future);

        StepVerifier.create(adapter.send(validRequest())
                .contextWrite(Context.of(ApiConstants.HEADER_TRACE_ID, "trace-1")))
            .assertNext(result -> {
                assertFalse(result.isSuccess());
                assertEquals(ProcessingResultCodes.DEST_UNAUTHORIZED.name(), result.getErrorCode());
            })
            .verifyComplete();
    }
}
