package com.example.fileprocessor.infrastructure.drivenadapters.aws;

import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.port.out.FileGateway;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.drivenadapters.aws.config.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@org.springframework.context.annotation.Profile("s3")
@Slf4j
@Component
public class S3GatewayAdapter implements FileGateway {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String S3_KEY_PREFIX = "documents/";

    private final S3AsyncClient s3Client;
    private final S3Properties s3Properties;

    public S3GatewayAdapter(S3AsyncClient s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    @Override
    public Mono<FileUploadResult> send(DocumentSendRequest request) {
        byte[] content = request.getFileContent();
        String key = buildKey(request);

        PutObjectRequest putRequest = PutObjectRequest.builder()
            .bucket(s3Properties.bucketName())
            .key(key)
            .contentType(request.getContentType())
            .contentLength((long) content.length)
            .metadata(java.util.Map.of(
                "traceId", request.getTraceId(),
                "originalFilename", request.getFilename()
            ))
            .build();

        CompletableFuture<PutObjectResponse> future = s3Client.putObject(putRequest, AsyncRequestBody.fromBytes(content));

        return Mono.fromFuture(future)
            .timeout(DEFAULT_TIMEOUT)
            .retryWhen(Retry.backoff(s3Properties.retryAttempts(), Duration.ofMillis(s3Properties.retryBackoffMillis()))
                .filter(this::isRetryableException)
                .doBeforeRetry(retrySignal -> {
                    long attempt = retrySignal.totalRetries() + 1;
                    log.warn("Retrying S3 upload for traceId={}, attempt {}/{} (backoff={}ms)",
                        request.getTraceId(),
                        attempt,
                        s3Properties.retryAttempts(),
                        s3Properties.retryBackoffMillis() * attempt);
                }))
            .map(completed -> {
                log.info("S3 upload successful: {} -> {}/{}",
                    request.getFilename(), s3Properties.bucketName(), key);
                return FileUploadResult.builder()
                    .status(DocumentStatus.SUCCESS.name())
                    .message("Uploaded to S3: " + s3Properties.bucketName() + "/" + key)
                    .correlationId(completed.eTag())
                    .traceId(request.getTraceId())
                    .processedAt(Instant.now())
                    .externalReference(key)
                    .success(true)
                    .build();
            })
            .onErrorResume(error -> handleS3Error(error, request));
    }

    private Mono<FileUploadResult> handleS3Error(Throwable error, DocumentSendRequest request) {
        log.error("S3 upload failed for {}: {}", request.getFilename(), error.getMessage());

        if (error instanceof java.util.concurrent.TimeoutException) {
            return Mono.just(FileUploadResult.builder()
                .status(DocumentStatus.FAILURE.name())
                .errorCode(ProcessingResultCodes.GATEWAY_TIMEOUT)
                .traceId(request.getTraceId())
                .processedAt(Instant.now())
                .success(false)
                .build());
        }

        // For S3 errors, all are treated as failures (S3 doesn't distinguish 4xx vs 5xx like HTTP)
        return Mono.just(FileUploadResult.builder()
            .status(DocumentStatus.FAILURE.name())
            .errorCode(ProcessingResultCodes.UNKNOWN_ERROR)
            .traceId(request.getTraceId())
            .processedAt(Instant.now())
            .success(false)
            .build());
    }

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof java.util.concurrent.TimeoutException) {
            return true;
        }
        if (throwable instanceof software.amazon.awssdk.core.exception.SdkException e) {
            String name = e.getClass().getSimpleName();
            return name.contains("ServiceException") || name.contains("SocketTimeoutException")
                || name.contains("ConnectTimeoutException");
        }
        return false;
    }

    private String buildKey(DocumentSendRequest request) {
        String sanitizedFilename = sanitizeFilename(request.getFilename());
        return String.format(S3_KEY_PREFIX + "%s/%s",
            request.getTraceId(),
            sanitizedFilename);
    }

    /**
     * Sanitizes filename to prevent path traversal attacks.
     * Removes ../ sequences and normalizes the path.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unnamed";
        }

        // Remove any path components that attempt traversal
        String sanitized = filename
            .replace("..", "")
            .replace("/", "")
            .replace("\\", "");

        // If result is empty or only whitespace, use default
        if (sanitized.isBlank()) {
            return "unnamed";
        }

        return sanitized;
    }
}
