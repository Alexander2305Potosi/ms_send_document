package com.example.fileprocessor.infrastructure.drivenadapters.aws;

import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.infrastructure.drivenadapters.aws.config.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class S3GatewayImpl implements S3Gateway {

    private final S3AsyncClient s3Client;
    private final S3Properties s3Properties;

    public S3GatewayImpl(S3AsyncClient s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    @Override
    public Mono<S3UploadResult> upload(SoapRequest request) {
        byte[] content = Base64.getDecoder().decode(request.getFileContentBase64());
        String key = buildKey(request);

        PutObjectRequest putRequest = PutObjectRequest.builder()
            .bucket(s3Properties.bucketName())
            .key(key)
            .contentType(request.getContentType())
            .contentLength((long) content.length)
            .metadata(java.util.Map.of(
                "traceId", request.getTraceId(),
                "originalFilename", request.getFilename(),
                "timestamp", request.getTimestamp().toString()
            ))
            .build();

        CompletableFuture<PutObjectResponse> future = s3Client.putObject(putRequest, AsyncRequestBody.fromBytes(content));

        return Mono.fromFuture(future)
            .map(completed -> {
                S3UploadResult result = new S3UploadResult(
                    s3Properties.bucketName(),
                    key,
                    completed.eTag(),
                    content.length,
                    request.getContentType()
                );
                log.info("S3 upload successful: {} -> {}/{}",
                    request.getFilename(), s3Properties.bucketName(), result.key());
                return result;
            })
            .doOnError(error -> log.error("S3 upload failed for {}: {}",
                request.getFilename(), error.getMessage()));
    }

    private String buildKey(SoapRequest request) {
        String timestamp = request.getTimestamp().toString().replace(":", "-");
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        return String.format("documents/%s/%s_%s",
            timestamp.substring(0, 10),
            uniqueId,
            request.getFilename());
    }
}