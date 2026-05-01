package com.example.fileprocessor.infrastructure.drivenadapters.aws;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.drivenadapters.aws.config.S3Properties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

@org.springframework.context.annotation.Profile("s3")
@Slf4j
@Component
public class S3GatewayAdapter implements S3Gateway {

    private final S3AsyncClient s3Client;
    private final S3Properties s3Properties;

    public S3GatewayAdapter(S3AsyncClient s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    @Override
    public Mono<FileUploadResult> send(FileUploadRequest request) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.get(ApiConstants.HEADER_TRACE_ID);
            log.info("Sending S3 upload request for documentId: {}, traceId: {}", request.getDocumentId(), traceId);

            byte[] content = request.getContent();
            if (content == null || content.length == 0) {
                log.warn("S3 upload skipped for documentId={} - content is null or empty", request.getDocumentId());
                return Mono.just(FileUploadResult.builder()
                    .status(DocumentStatus.FAILURE.name())
                    .errorCode(ProcessingResultCodes.EMPTY_CONTENT)
                    .traceId(traceId)
                    .message("Cannot upload empty content to S3")
                    .processedAt(Instant.now())
                    .success(false)
                    .build());
            }

            String key = buildKey(traceId, request.getFilename());

            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(s3Properties.bucketName())
                .key(key)
                .contentType(request.getContentType())
                .contentLength((long) content.length)
                .metadata(Map.of(
                    "traceId", traceId,
                    "originalFilename", request.getFilename(),
                    "documentId", request.getDocumentId()
                ))
                .build();

            CompletableFuture<PutObjectResponse> future = s3Client.putObject(putRequest, AsyncRequestBody.fromBytes(content));

            return Mono.fromFuture(future)
                .timeout(Duration.ofSeconds(s3Properties.timeoutSeconds()))
                .retryWhen(Retry.backoff(s3Properties.retryAttempts(), Duration.ofMillis(s3Properties.retryBackoffMillis()))
                    .filter(this::isRetryableException)
                    .doBeforeRetry(retrySignal -> {
                        long attempt = retrySignal.totalRetries() + 1;
                        log.warn("Retrying S3 upload for documentId={}, attempt {}/{} (backoff={}ms)",
                            request.getDocumentId(), attempt, s3Properties.retryAttempts(),
                            s3Properties.retryBackoffMillis() * attempt);
                    }))
                .map(completed -> {
                    log.info("S3 upload successful: {} -> {}/{}", request.getFilename(), s3Properties.bucketName(), key);
                    return FileUploadResult.builder()
                        .status(DocumentStatus.SUCCESS.name())
                        .message("Uploaded to S3: " + s3Properties.bucketName() + "/" + key)
                        .correlationId(completed.eTag())
                        .traceId(traceId)
                        .processedAt(Instant.now())
                        .externalReference(key)
                        .success(true)
                        .build();
                })
                .onErrorResume(error -> handleS3Error(error, request.getDocumentId(), traceId));
        });
    }

    private Mono<FileUploadResult> handleS3Error(Throwable error, String documentId, String traceId) {
        log.error("S3 upload failed for documentId {}: {}", documentId, error.getMessage());

        if (error instanceof TimeoutException) {
            return Mono.just(FileUploadResult.builder()
                .status(DocumentStatus.FAILURE.name())
                .errorCode(S3ErrorCodes.GATEWAY_TIMEOUT)
                .traceId(traceId)
                .processedAt(Instant.now())
                .success(false)
                .build());
        }

        String errorCode = categorizeS3Error(error);
        log.error("S3 error categorized as {} for documentId {}", errorCode, documentId);

        return Mono.just(FileUploadResult.builder()
            .status(DocumentStatus.FAILURE.name())
            .errorCode(errorCode)
            .traceId(traceId)
            .processedAt(Instant.now())
            .success(false)
            .build());
    }

    private String categorizeS3Error(Throwable error) {
        if (error instanceof software.amazon.awssdk.services.s3.model.S3Exception e) {
            Integer statusCode = e.statusCode();
            if (statusCode != null) {
                if (statusCode == 403) {
                    return S3ErrorCodes.ACCESS_DENIED;
                }
                if (statusCode == 404) {
                    return S3ErrorCodes.NOT_FOUND;
                }
                if (statusCode == 503) {
                    return S3ErrorCodes.SERVICE_UNAVAILABLE;
                }
            }
            String awsErrorCode = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : null;
            if ("Throttling".equals(awsErrorCode) || "ThrottlingException".equals(awsErrorCode)) {
                return S3ErrorCodes.SERVICE_UNAVAILABLE;
            }
            if ("AccessDenied".equals(awsErrorCode)) {
                return S3ErrorCodes.ACCESS_DENIED;
            }
        }
        if (error instanceof software.amazon.awssdk.core.exception.SdkException e) {
            String name = e.getClass().getSimpleName();
            if (name.contains("ServiceException") || name.contains("SocketTimeout")
                || name.contains("ConnectTimeout")) {
                return S3ErrorCodes.SERVICE_UNAVAILABLE;
            }
        }
        return S3ErrorCodes.UNKNOWN_ERROR;
    }

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof TimeoutException) return true;
        if (throwable instanceof software.amazon.awssdk.core.exception.SdkException e) {
            String name = e.getClass().getSimpleName();
            return name.contains("ServiceException") || name.contains("SocketTimeoutException")
                || name.contains("ConnectTimeoutException");
        }
        return false;
    }

    private String buildKey(String traceId, String filename) {
        String sanitizedFilename = sanitizeFilename(filename);
        return String.format(s3Properties.keyPrefix() + "%s/%s", traceId, sanitizedFilename);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "unnamed";
        String sanitized = filename.replaceAll("[\\x00-\\x1F\\x7F]", "");
        sanitized = sanitized.replace("..", "").replace("/", "").replace("\\", "");
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "");
        if (sanitized.isBlank()) return "unnamed";
        return sanitized;
    }
}
