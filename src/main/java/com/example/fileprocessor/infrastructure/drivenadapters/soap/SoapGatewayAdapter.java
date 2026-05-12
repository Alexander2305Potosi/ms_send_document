package com.example.fileprocessor.infrastructure.drivenadapters.soap;

import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.mapper.SoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
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
 * Fixed to unwrap RetryExhaustedException and capture the underlying SOAP Fault.
 */
@Component
@RequiredArgsConstructor
public class SoapGatewayAdapter implements SoapGateway {

    private static final Logger LOGGER = Logger.getLogger(SoapGatewayAdapter.class.getName());

    private final WebClient soapWebClient;
    private final SoapProperties properties;
    private final SoapMapper mapper;

    @Override
    public Mono<FileUploadResponse> send(FileUploadRequest request) {
        return Mono.deferContextual(ctx -> {
            final String traceId = ctx.getOrDefault(ApiConstants.HEADER_TRACE_ID, "unknown");

            return soapWebClient.post()
                .contentType(MediaType.TEXT_XML)
                .header("SOAPAction", properties.soapAction() != null ? properties.soapAction() : "")
                .bodyValue(mapper.buildEnvelope(request, properties, traceId))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .retryWhen(Retry.backoff(properties.retryAttempts(), Duration.ofMillis(500))
                    .filter(this::isRetryable))
                .map(xml -> mapper.parseResponse(xml, traceId))
                .map(res -> buildResponse(res, traceId))
                .onErrorResume(error -> {
                    // DESENVOLVER CAUSA: Si Reactor agotó reintentos, sacamos la causa real
                    Throwable realError = error;
                    if (error.getClass().getSimpleName().contains("RetryExhausted") && error.getCause() != null) {
                        realError = error.getCause();
                        LOGGER.log(Level.FINE, "Unwrapped RetryExhaustedException to: {0}", realError.getClass().getName());
                    }
                    return handleFinalError(realError, traceId);
                });
        });
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException wce) {
            int code = wce.getStatusCode().value();
            return code == 500 || code == 503 || code == 504 || code == 429;
        }
        return throwable instanceof TimeoutException || throwable instanceof ConnectException;
    }

    private Mono<FileUploadResponse> handleFinalError(Throwable error, String traceId) {
        if (error instanceof WebClientResponseException wce) {
            String rawBody = wce.getResponseBodyAsString();
            if (isXml(rawBody)) {
                try {
                    ExternalServiceResponse parsed = mapper.parseResponse(rawBody, traceId);
                    return Mono.just(buildResponse(parsed, traceId));
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Failed to parse Fault from error body", e);
                }
            }
        }
        return Mono.just(buildErrorResult(error, traceId));
    }

    private boolean isXml(String body) {
        return body != null && body.trim().startsWith("<") && !body.toLowerCase().contains("<html");
    }

    private FileUploadResponse buildResponse(ExternalServiceResponse response, String traceId) {
        String errorCode = !response.isSuccess() ? ProcessingResultCodes.SOAP_ERROR.name() : null;
        return FileUploadResponse.builder()
                .status(response.getStatus())
                .message(response.getMessage())
                .correlationId(response.getCorrelationId())
                .errorCode(errorCode)
                .traceId(traceId)
                .processedAt(response.getProcessedAt())
                .externalReference(response.getExternalReference())
                .success(response.isSuccess())
                .build();
    }

    private FileUploadResponse buildErrorResult(Throwable error, String traceId) {
        String errorCode = mapErrorCode(error);
        String message = error.getMessage();

        if (error instanceof WebClientResponseException wce) {
            message = String.format("HTTP %d - %s", wce.getStatusCode().value(), wce.getStatusText());
        } else if (error instanceof TimeoutException) {
            message = "Timeout: El servicio no respondió en " + properties.timeoutSeconds() + " segundos";
        }

        return FileUploadResponse.builder()
                .status(ProcessingResultCodes.FAILURE.name())
                .errorCode(errorCode)
                .traceId(traceId)
                .message(message != null ? message : "Error desconocido")
                .processedAt(Instant.now())
                .success(false)
                .build();
    }

    private String mapErrorCode(Throwable error) {
        if (error instanceof WebClientResponseException) return ProcessingResultCodes.BAD_GATEWAY.name();
        if (error instanceof TimeoutException) return ProcessingResultCodes.GATEWAY_TIMEOUT.name();
        return ProcessingResultCodes.UNKNOWN_ERROR.name();
    }
}
