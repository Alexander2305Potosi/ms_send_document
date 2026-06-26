package com.example.fileprocessor.infrastructure.drivenadapters.aws;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.DEST_UNAUTHORIZED;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.EMPTY_CONTENT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILURE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.GATEWAY_TIMEOUT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SERVICE_UNAVAILABLE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SOURCE_NOT_FOUND;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SUCCESS;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.UNKNOWN_ERROR;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.drivenadapters.aws.config.S3Properties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
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
import java.util.logging.Level;
import java.util.logging.Logger;

@org.springframework.context.annotation.Profile("s3")
@Component
public class S3GatewayAdapter implements S3Gateway {

    private static final Logger LOGGER = Logger.getLogger(S3GatewayAdapter.class.getName());

    private final S3AsyncClient s3Client;
    private final S3Properties s3Properties;

    public S3GatewayAdapter(S3AsyncClient s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    @Override
    public Mono<FileUploadResponse> send(FileUploadRequest request) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault(ApiConstants.HEADER_TRACE_ID, "unknown");
            LOGGER.log(Level.INFO, "Sending S3 upload request for documentId: {0}, traceId: {1}", new Object[]{request.getDocumentId(), traceId});

            byte[] content = request.getContent();
            if (content == null || content.length == 0) {
                LOGGER.log(Level.WARNING, "S3 upload skipped for documentId={0} - content is null or empty", new Object[]{request.getDocumentId()});
                return Mono.just(FileUploadResponse.builder()
                    .status(FAILURE.name())
                    .syncStatus(EMPTY_CONTENT.name())
                    .traceId(traceId)
                    .message(EMPTY_CONTENT.value())
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

            return Mono.defer(() -> {
                    CompletableFuture<PutObjectResponse> future = s3Client.putObject(putRequest, AsyncRequestBody.fromBytes(content));
                    return Mono.fromFuture(future);
                })
                .timeout(Duration.ofSeconds(s3Properties.timeoutSeconds()))
                .retryWhen(Retry.backoff(s3Properties.retryAttempts(), Duration.ofMillis(s3Properties.retryBackoffMillis()))
                    .filter(this::isRetryableException)
                    .doBeforeRetry(retrySignal -> {
                        long attempt = retrySignal.totalRetries() + 1;
                        LOGGER.log(Level.WARNING, "Retrying S3 upload for documentId={0}, attempt {1}/{2}",
                            new Object[]{request.getDocumentId(), attempt, s3Properties.retryAttempts()});
                    }))
                .map(completed -> {
                    LOGGER.log(Level.INFO, "S3 upload successful: {0} -> {1}/{2}", new Object[]{request.getFilename(), s3Properties.bucketName(), key});
                    return FileUploadResponse.builder()
                        .status(SUCCESS.name())
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

    private Mono<FileUploadResponse> handleS3Error(Throwable error, String documentId, String traceId) {
        Throwable actualError = error;
        if (error.getCause() != null && error.getClass().getName().contains("RetryExhausted")) {
            actualError = error.getCause();
        }

        LOGGER.log(Level.SEVERE, "S3 upload failed for documentId {0}: {1}", new Object[]{documentId, actualError.getMessage()});

        String syncStatus = categorizeS3Error(actualError);
        return Mono.just(FileUploadResponse.builder()
            .status(FAILURE.name())
            .syncStatus(syncStatus)
            .traceId(traceId)
            .message(actualError.getMessage())
            .processedAt(Instant.now())
            .success(false)
            .build());
    }

    String categorizeS3Error(Throwable error) {
        if (error instanceof TimeoutException) return GATEWAY_TIMEOUT.name();
        
        if (error instanceof software.amazon.awssdk.services.s3.model.S3Exception e) {
            Integer statusCode = e.statusCode();
            if (statusCode != null) {
                if (statusCode == 403) return DEST_UNAUTHORIZED.name();
                if (statusCode == 404) return SOURCE_NOT_FOUND.name();
                if (statusCode == 503) return SERVICE_UNAVAILABLE.name();
            }
        }
        return UNKNOWN_ERROR.name();
    }

    boolean isRetryableException(Throwable throwable) {
        return throwable instanceof TimeoutException || 
               throwable instanceof software.amazon.awssdk.core.exception.SdkException;
    }

    String buildKey(String traceId, String filename) {
        String sanitizedFilename = sanitizeFilename(filename);
        return String.format(s3Properties.keyPrefix() + "%s/%s", traceId, sanitizedFilename);
    }

    String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "unnamed";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "");
    }
}
