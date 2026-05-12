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
 * Enhanced to capture and parse detailed error responses (SOAP Faults) for traceability.
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

            LOGGER.log(Level.INFO, "[SOAP] Starting upload for file: {0}, traceId: {1}", 
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
                    .doBeforeRetry(signal -> LOGGER.log(Level.WARNING, 
                        "[SOAP] Retry attempt {0}/{1} for traceId: {2}. Reason: {3}",
                        new Object[]{signal.totalRetries() + 1, properties.retryAttempts(), traceId, signal.failure().getMessage()})))
                .map(xml -> mapper.parseResponse(xml, traceId))
                .map(this::buildSuccessResult)
                .onErrorResume(error -> handleFinalError(error, traceId));
        });
    }

    /**
     * Decisions on whether to retry based on the type of error.
     * Retries are for infrastructure issues (503, 504, 500) and timeouts.
     */
    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException wce) {
            int code = wce.getStatusCode().value();
            // Retentamos en errores de servidor temporales o limitación de tasa
            return code == 500 || code == 503 || code == 504 || code == 429;
        }
        return throwable instanceof TimeoutException || 
               throwable instanceof ConnectException ||
               throwable instanceof org.springframework.web.reactive.function.client.WebClientRequestException;
    }

    /**
     * Detailed error handler that attempts to parse the response body if it's an XML/SOAP Fault.
     */
    private Mono<FileUploadResponse> handleFinalError(Throwable error, String traceId) {
        if (error instanceof WebClientResponseException wce) {
            String rawBody = wce.getResponseBodyAsString();
            
            // Si el cuerpo parece un XML (y no un HTML de error genérico)
            if (isXml(rawBody)) {
                try {
                    LOGGER.log(Level.WARNING, "[SOAP] HTTP Error {0} detected, parsing XML Fault detail for traceId: {1}", 
                            new Object[]{wce.getStatusCode(), traceId});
                    ExternalServiceResponse parsed = mapper.parseResponse(rawBody, traceId);
                    return Mono.just(buildSuccessResult(parsed));
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Failed to parse error body as SOAP, falling back to generic error", e);
                }
            }
        }
        return Mono.just(buildErrorResult(error, traceId));
    }

    private boolean isXml(String body) {
        return body != null && body.trim().startsWith("<") && !body.toLowerCase().contains("<html");
    }

    private FileUploadResponse buildSuccessResult(ExternalServiceResponse response) {
        return FileUploadResponse.builder()
                .status(response.getStatus())
                .message(response.getMessage())
                .correlationId(response.getCorrelationId())
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
            message = "Timeout after " + properties.timeoutSeconds() + "s";
        }

        LOGGER.log(Level.SEVERE, "[SOAP] Final failure for traceId: {0}, code: {1}, message: {2}",
                new Object[]{traceId, errorCode, message});

        return FileUploadResponse.builder()
                .status(ProcessingResultCodes.FAILURE.name())
                .errorCode(errorCode)
                .traceId(traceId)
                .message(message)
                .processedAt(Instant.now())
                .success(false)
                .build();
    }

    private String mapErrorCode(Throwable error) {
        if (error instanceof WebClientResponseException) return ProcessingResultCodes.BAD_GATEWAY.name();
        if (error instanceof TimeoutException) return ProcessingResultCodes.GATEWAY_TIMEOUT.name();
        if (error instanceof ConnectException || 
            error instanceof org.springframework.web.reactive.function.client.WebClientRequestException) {
            return ProcessingResultCodes.SERVICE_UNAVAILABLE.name();
        }
        return ProcessingResultCodes.UNKNOWN_ERROR.name();
    }
}
