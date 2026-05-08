package com.example.fileprocessor.infrastructure.drivenadapters.soap;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.mapper.SoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.ConnectException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Unified and streamlined SOAP gateway adapter.
 * Refactored to remove unused variables and redundant logic.
 */
@Component
@RequiredArgsConstructor
public class SoapGatewayAdapter implements SoapGateway {

    private static final Logger log = Logger.getLogger(SoapGatewayAdapter.class.getName());

    private final WebClient soapWebClient;
    private final SoapProperties properties;
    private final SoapMapper mapper;

    @Override
    public Mono<FileUploadResult> send(FileUploadRequest request) {
        return Mono.deferContextual(ctx -> {
            final String traceId = ctx.getOrDefault(ApiConstants.HEADER_TRACE_ID, "unknown");

            log.log(Level.INFO, "[SOAP] Starting upload for file: {0}, traceId: {1}", 
                    new Object[]{request.getFilename(), traceId});

            return soapWebClient.post()
                .contentType(MediaType.TEXT_XML)
                .header("SOAPAction", properties.soapAction() != null ? properties.soapAction() : "")
                .bodyValue(mapper.buildEnvelope(request, properties, traceId))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .retryWhen(Retry.backoff(properties.retryAttempts(), Duration.ofMillis(500))
                    .filter(this::isRetryable)
                    .doBeforeRetry(signal -> log.log(Level.WARNING, 
                        "[SOAP] Retry attempt {0}/{1} for traceId: {2}. Reason: {3}",
                        new Object[]{signal.totalRetries() + 1, properties.retryAttempts(), traceId, signal.failure().getMessage()})))
                .map(xml -> mapper.parseResponse(xml, traceId))
                .map(this::buildSuccessResult)
                .onErrorResume(error -> Mono.just(buildErrorResult(error, traceId)));
        });
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException wce) {
            int code = wce.getStatusCode().value();
            return code == 503 || code == 502 || code == 504 || code == 429;
        }
        return throwable instanceof TimeoutException || throwable instanceof ConnectException;
    }

    private FileUploadResult buildSuccessResult(ExternalServiceResponse response) {
        return FileUploadResult.builder()
                .status(response.getStatus())
                .message(response.getMessage())
                .correlationId(response.getCorrelationId())
                .processedAt(response.getProcessedAt())
                .externalReference(response.getExternalReference())
                .success(response.isSuccess())
                .build();
    }

    private FileUploadResult buildErrorResult(Throwable error, String traceId) {
        String errorCode = mapErrorCode(error);
        String message = error.getMessage();

        if (error instanceof TimeoutException) {
            message = "Timeout after " + properties.timeoutSeconds() + "s";
        }

        log.log(Level.SEVERE, "[SOAP] Final failure for traceId: {0}, code: {1}, message: {2}",
                new Object[]{traceId, errorCode, message});

        return FileUploadResult.builder()
                .status(DocumentStatus.FAILURE.name())
                .errorCode(errorCode)
                .traceId(traceId)
                .message(message)
                .processedAt(Instant.now())
                .success(false)
                .build();
    }

    private String mapErrorCode(Throwable error) {
        if (error instanceof WebClientResponseException) return "BAD_GATEWAY";
        if (error instanceof TimeoutException) return "GATEWAY_TIMEOUT";
        if (error instanceof ConnectException) return "SERVICE_UNAVAILABLE";
        return "UNKNOWN_ERROR";
    }
}
