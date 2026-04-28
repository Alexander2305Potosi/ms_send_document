package com.example.fileprocessor.infrastructure.drivenadapters.aws;

import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.port.out.FileGateway;
import com.example.fileprocessor.infrastructure.drivenadapters.aws.config.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@org.springframework.context.annotation.Profile("s3")
@Slf4j
@Component
public class S3GatewayAdapter implements FileGateway {

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
            .map(completed -> {
                log.info("S3 upload successful: {} -> {}/{}",
                    request.getFilename(), s3Properties.bucketName(), key);
                return FileUploadResult.builder()
                    .status(DocumentStatus.SUCCESS_VALUE)
                    .message("Uploaded to S3: " + s3Properties.bucketName() + "/" + key)
                    .correlationId(completed.eTag())
                    .traceId(request.getTraceId())
                    .processedAt(Instant.now())
                    .externalReference(key)
                    .success(true)
                    .build();
            })
            .doOnError(error -> log.error("S3 upload failed for {}: {}",
                request.getFilename(), error.getMessage()))
            .onErrorResume(error -> Mono.just(
                FileUploadResult.builder()
                    .status(DocumentStatus.FAILURE_VALUE)
                    .message("S3 upload failed: " + error.getMessage())
                    .traceId(request.getTraceId())
                    .processedAt(Instant.now())
                    .success(false)
                    .build()));
    }

    private String buildKey(DocumentSendRequest request) {
        String timestamp = java.time.Instant.now().toString().replace(":", "-");
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        return String.format("documents/%s/%s_%s",
            timestamp.substring(0, 10),
            uniqueId,
            request.getFilename());
    }
}
