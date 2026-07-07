package com.example.fileprocessor.infrastructure.drivenadapters.soap;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILED;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.GATEWAY_TIMEOUT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.INVALID_RESPONSE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.UNKNOWN_ERROR;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.drivenadapters.AdapterErrorMapper;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.mapper.SoapMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Unified and streamlined SOAP gateway adapter.
 * Fixed to unwrap RetryExhaustedException and capture the underlying SOAP
 * Fault.
 */
@Component
public class SoapGatewayAdapter implements SoapGateway {

    private static final Logger LOGGER = Logger.getLogger(SoapGatewayAdapter.class.getName());

    private final WebClient soapWebClient;
    private final SoapProperties properties;
    private final SoapMapper mapper;

    public SoapGatewayAdapter(WebClient.Builder webClientBuilder, SoapProperties properties, SoapMapper mapper) {
        this.soapWebClient = webClientBuilder
                .baseUrl(properties.endpoint())
                .build();
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public Flux<FileUploadResponse> send(FileUploadRequest request) {
        return Flux.deferContextual(ctx -> {
            final String messageId = ctx.getOrDefault(ApiConstants.HEADER_TRACE_ID, "unknown");
            final String traceId = UUID.randomUUID().toString();
            LOGGER.log(Level.INFO, "message id: {0} : {1}",new Object[]{messageId, request});
            return sendWithRetry(request, traceId, 1);
        });
    }

    private Flux<FileUploadResponse> sendWithRetry(FileUploadRequest request, String traceId, int attempt) {
        return soapWebClient.post()
                .contentType(MediaType.TEXT_XML)
                .header("SOAPAction", properties.soapAction() != null ? properties.soapAction() : "")
                .bodyValue(mapper.buildEnvelope(request, traceId))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .switchIfEmpty(Mono.error(new com.example.fileprocessor.domain.exception.ProcessingException(
                        INVALID_RESPONSE.value(),
                        INVALID_RESPONSE.name(), traceId)))
                .map(xml -> mapper.parseResponse(xml, traceId).toBuilder()
                        .traceId(traceId)
                        .attemptCount(attempt)
                        .build())
                .onErrorResume(error -> handleFinalError(error, traceId)
                        .map(errorResp -> errorResp.toBuilder().attemptCount(attempt).build()))
                .flatMapMany(response -> {
                    boolean isRetryable = !response.isSuccess() &&
                                         ProcessingResultCodes.isTransient(response.getSyncStatus()) &&
                                         attempt <= properties.retryAttempts();

                    if (isRetryable) {
                        LOGGER.log(Level.INFO, "[TraceID: {0}] Technical retry {1}/{2} due to: {3}",
                                new Object[]{traceId, attempt, properties.retryAttempts(), response.getMessage()});

                        return Flux.just(response.toBuilder().technicalRetry(true).build())
                                .concatWith(Mono.delay(Duration.ofMillis(500))
                                        .flatMapMany(unused -> sendWithRetry(request, traceId, attempt + 1)));
                    }
                    return Flux.just(response.toBuilder().technicalRetry(false).build());
                });
    }


    private Mono<FileUploadResponse> handleFinalError(Throwable error, String traceId) {
        if (error instanceof WebClientResponseException wce) {
            String rawBody = wce.getResponseBodyAsString();
            if (isXml(rawBody)) {
                try {
                    return Mono.just(mapper.parseResponse(rawBody, traceId));
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Failed to parse Fault from error body", e);
                }
            }
        }

        // Delegate all HTTP/timeout/connection error mapping to the shared infrastructure utility
        String syncStatus = AdapterErrorMapper.resolveErrorCode(error);

        // Unwrap to find the root cause (e.g. SSLHandshakeException, ConnectException) for accurate messages
        Throwable root = error;
        while (root.getCause() != null && root != root.getCause()) {
            if (root instanceof WebClientResponseException) {
                break;
            }
            root = root.getCause();
        }

        String message = root.getMessage();

        if (root instanceof WebClientResponseException wce) {
            message = String.format("HTTP %d - %s", wce.getStatusCode().value(), wce.getStatusText());
        } else if (syncStatus.equals(GATEWAY_TIMEOUT.name())) {
            message = "Timeout: El servicio no respondió en " + properties.timeoutSeconds() + " segundos";
        } else if (root instanceof java.net.ConnectException) {
            message = "Connection refused: El servicio no está disponible";
        }

        if (message == null || message.isBlank()) {
            message = error.getMessage();
        }

        return Mono.just(FileUploadResponse.builder()
                .status(FAILED.name())
                .message(message != null && !message.isBlank() ? message : UNKNOWN_ERROR.value())
                .syncStatus(syncStatus)
                .traceId(traceId)
                .processedAt(Instant.now())
                .success(false)
                .build());
    }

    private boolean isXml(String body) {
        return body != null && body.trim().startsWith("<") && !body.toLowerCase().contains("<html");
    }
}
