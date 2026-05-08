package com.example.fileprocessor.infrastructure.drivenadapters.soap;

import com.example.fileprocessor.domain.entity.DocumentHistory;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.port.out.SoapGatewayV2;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.drivenadapters.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.config.SoapV2Properties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import com.example.fileprocessor.infrastructure.helpers.soap.SoapConstants;
import com.example.fileprocessor.infrastructure.helpers.soap.mapper.SoapMapper;
import com.example.fileprocessor.infrastructure.helpers.soap.v2.mapper.SoapV2Mapper;
import jakarta.annotation.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SOAP gateway adapter for file upload operations.
 * Implements both V1 (UploadFile) and V2 (transmitirDocumento) SOAP protocols.
 */
@Component
public class SoapGatewayAdapter implements SoapGateway, SoapGatewayV2 {

    private static final Logger log = Logger.getLogger(SoapGatewayAdapter.class.getName());

    private final WebClient webClient;
    private final WebClient webClientV2;
    private final SoapProperties properties;
    private final SoapV2Properties v2Properties;
    private final SoapMapper soapMapper;
    private final SoapV2Mapper soapV2Mapper;
    private final DocumentHistoryRepository historyRepository;

    public SoapGatewayAdapter(WebClient.Builder webClientBuilder,
                              SoapProperties properties,
                              SoapV2Properties v2Properties,
                              SoapMapper soapMapper,
                              SoapV2Mapper soapV2Mapper,
                              DocumentHistoryRepository historyRepository) {
        this.properties = properties;
        this.v2Properties = v2Properties;
        this.soapMapper = soapMapper;
        this.soapV2Mapper = soapV2Mapper;
        this.historyRepository = historyRepository;

        this.webClient = webClientBuilder
            .baseUrl(properties.endpoint())
            .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
            .build();

        this.webClientV2 = webClientBuilder
            .baseUrl(v2Properties.endpoint())
            .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
            .build();
    }

    @Override
    public Mono<FileUploadResult> send(FileUploadRequest request) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault(ApiConstants.HEADER_TRACE_ID, "unknown");
            int maxRetries = properties.retryAttempts();
            AtomicInteger attemptCount = new AtomicInteger(1);

            log.log(Level.INFO, "Sending SOAP V1 request for traceId: {0}, endpoint: {1}, retryAttempts: {2}",
                    new Object[]{traceId, properties.endpoint(), maxRetries});

            String soapEnvelope = soapMapper.toFullSoapMessage(request);

            return executeSoapCall(webClient, soapEnvelope, traceId,
                    properties.timeoutSeconds(), maxRetries,
                    SoapConstants.FILE_SERVICE + SoapConstants.SOAP_ACTION_UPLOAD,
                    attemptCount,
                    signal -> traceRetry(request, signal))
                .onErrorMap(e -> {
                    Throwable unwrapped = e;
                    while (unwrapped instanceof RuntimeException && unwrapped.getCause() != null && unwrapped.getCause() != unwrapped) {
                        unwrapped = unwrapped.getCause();
                    }
                    return unwrapped;
                })
                .map(soapMapper::fromSoapXml)
                .doOnNext(response -> log.log(Level.INFO,
                    "SOAP V1 response received for traceId={0}: correlationId={1}",
                    new Object[]{traceId, response.getCorrelationId()}))
                .map(response -> toFileUploadResult(response, attemptCount.get()))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.log(Level.SEVERE, "SOAP V1 HTTP error for traceId={0}: {1} {2}",
                            new Object[]{traceId, ex.getStatusCode(), ex.getMessage()});
                    return Mono.just(buildErrorResult(traceId, SoapErrorCodes.BAD_GATEWAY,
                            ex.getMessage(), attemptCount.get()));
                })
                .onErrorResume(TimeoutException.class, ex -> {
                    log.log(Level.SEVERE, "SOAP V1 timeout for traceId={0}", traceId);
                    return Mono.just(buildErrorResult(traceId, SoapErrorCodes.GATEWAY_TIMEOUT,
                            "Timeout after " + properties.timeoutSeconds() + "s", attemptCount.get()));
                })
                .onErrorResume(IOException.class, ex -> {
                    log.log(Level.SEVERE, "SOAP V1 IO error for traceId={0}: {1}",
                            new Object[]{traceId, ex.getMessage()});
                    return Mono.just(buildErrorResult(traceId, SoapErrorCodes.UNKNOWN_ERROR,
                            ex.getMessage(), attemptCount.get()));
                })
                .onErrorResume(ConnectException.class, ex -> {
                    log.log(Level.SEVERE, "SOAP V1 connection error for traceId={0}: {1}",
                            new Object[]{traceId, ex.getMessage()});
                    return Mono.just(buildErrorResult(traceId, SoapErrorCodes.SERVICE_UNAVAILABLE,
                            ex.getMessage(), attemptCount.get()));
                })
                .onErrorResume(Throwable.class, ex -> {
                    log.log(Level.SEVERE, "SOAP V1 unexpected error for traceId={0}: {1}",
                            new Object[]{traceId, ex.getMessage()});
                    return Mono.just(buildErrorResult(traceId, SoapErrorCodes.UNKNOWN_ERROR,
                            ex.getMessage(), attemptCount.get()));
                });
        });
    }

    @Override
    public Mono<FileUploadResult> transmitirDocumento(FileUploadRequest request) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault(ApiConstants.HEADER_TRACE_ID, "unknown");
            int maxRetries = v2Properties.retryAttempts();
            AtomicInteger attemptCount = new AtomicInteger(1);

            log.log(Level.INFO, "Sending SOAP V2 request for traceId: {0}, endpoint: {1}, retryAttempts: {2}",
                    new Object[]{traceId, v2Properties.endpoint(), maxRetries});

            String soapEnvelope = soapV2Mapper.buildEnvelope(request, v2Properties, traceId);

            return executeSoapCall(webClientV2, soapEnvelope, traceId,
                    v2Properties.timeoutSeconds(), maxRetries,
                    v2Properties.soapAction(),
                    attemptCount,
                    signal -> traceRetry(request, signal))
                .onErrorMap(e -> {
                    Throwable unwrapped = e;
                    while (unwrapped instanceof RuntimeException && unwrapped.getCause() != null && unwrapped.getCause() != unwrapped) {
                        unwrapped = unwrapped.getCause();
                    }
                    return unwrapped;
                })
                .map(xml -> soapV2Mapper.parseResponse(xml, traceId))
                .doOnNext(response -> log.log(Level.INFO,
                    "SOAP V2 response received for traceId={0}: correlationId={1}",
                    new Object[]{traceId, response.getCorrelationId()}))
                .map(response -> toFileUploadResult(response, attemptCount.get()))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.log(Level.SEVERE, "SOAP V2 HTTP error for traceId={0}: {1} {2}",
                            new Object[]{traceId, ex.getStatusCode(), ex.getMessage()});
                    return Mono.just(buildErrorResult(traceId, SoapErrorCodes.BAD_GATEWAY,
                            ex.getMessage(), attemptCount.get()));
                })
                .onErrorResume(TimeoutException.class, ex -> {
                    log.log(Level.SEVERE, "SOAP V2 timeout for traceId={0}", traceId);
                    return Mono.just(buildErrorResult(traceId, SoapErrorCodes.GATEWAY_TIMEOUT,
                            "Timeout after " + v2Properties.timeoutSeconds() + "s", attemptCount.get()));
                })
                .onErrorResume(IOException.class, ex -> {
                    log.log(Level.SEVERE, "SOAP V2 IO error for traceId={0}: {1}",
                            new Object[]{traceId, ex.getMessage()});
                    return Mono.just(buildErrorResult(traceId, SoapErrorCodes.UNKNOWN_ERROR,
                            ex.getMessage(), attemptCount.get()));
                })
                .onErrorResume(ConnectException.class, ex -> {
                    log.log(Level.SEVERE, "SOAP V2 connection error for traceId={0}: {1}",
                            new Object[]{traceId, ex.getMessage()});
                    return Mono.just(buildErrorResult(traceId, SoapErrorCodes.SERVICE_UNAVAILABLE,
                            ex.getMessage(), attemptCount.get()));
                })
                .onErrorResume(Throwable.class, ex -> {
                    log.log(Level.SEVERE, "SOAP V2 unexpected error for traceId={0}: {1}",
                            new Object[]{traceId, ex.getMessage()});
                    return Mono.just(buildErrorResult(traceId, SoapErrorCodes.UNKNOWN_ERROR,
                            ex.getMessage(), attemptCount.get()));
                });
        });
    }

    private Mono<String> executeSoapCall(WebClient client,
                                         String soapEnvelope,
                                         String traceId,
                                         int timeoutSeconds,
                                         int maxRetries,
                                         @Nullable String soapAction,
                                         AtomicInteger attemptCount,
                                         @Nullable Consumer<reactor.util.retry.Retry.RetrySignal> onRetry) {

        WebClient.RequestBodySpec bodySpec = client.post()
            .contentType(MediaType.TEXT_XML);

        bodySpec.header("SOAPAction", soapAction != null ? soapAction : "");

        Mono<String> httpCall = Mono.defer(() -> bodySpec
            .bodyValue(soapEnvelope)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .doOnError(e -> {
                int currentAttempt = attemptCount.get();
                log.log(Level.WARNING, "SOAP attempt {0}/{1} failed for traceId={2}: {3}",
                        new Object[]{currentAttempt, maxRetries + 1, traceId, e.getMessage()});
            }));

        if (maxRetries > 0) {
            return httpCall.retryWhen(reactor.util.retry.Retry.backoff(maxRetries, Duration.ofMillis(500))
                .filter(this::isRetryable)
                .doBeforeRetry(signal -> {
                    int currentAttempt = attemptCount.incrementAndGet();
                    log.log(Level.INFO, "Retrying SOAP for traceId={0}, attempt {1}/{2}",
                            new Object[]{traceId, currentAttempt, maxRetries + 1});
                    if (onRetry != null) {
                        onRetry.accept(signal);
                    }
                }));
        }

        return httpCall;
    }

    boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException wce) {
            int statusCode = wce.getStatusCode().value();
            return statusCode == 503 || statusCode == 502 || statusCode == 504 || statusCode == 429;
        }
        if (throwable instanceof TimeoutException || throwable instanceof ConnectException) {
            return true;
        }
        return false;
    }

    private void traceRetry(FileUploadRequest request, reactor.util.retry.Retry.RetrySignal signal) {
        int attempt = (int) signal.totalRetries() + 1;
        String errorCode = signal.failure() instanceof ProcessingException pe
            ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
        historyRepository.save(DocumentHistory.builder()
            .documentId(request.getDocId())
            .filename(request.getFilename())
            .operation("SOAP")
            .result(DocumentStatus.FAILURE.name())
            .errorCode(errorCode)
            .errorMessage(signal.failure().getMessage())
            .retry(attempt)
            .startedAt(LocalDateTime.now())
            .completedAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .build())
            .doOnError(e -> log.log(Level.WARNING, "Failed to record SOAP retry trace: {0}", e.getMessage()))
            .subscribe();
    }

    FileUploadResult buildErrorResult(String traceId, String errorCode, String message, int attemptCount) {
        return FileUploadResult.builder()
                .status(DocumentStatus.FAILURE.name())
                .errorCode(errorCode)
                .traceId(traceId)
                .message(message)
                .processedAt(Instant.now())
                .success(false)
                .attemptCount(attemptCount)
                .build();
    }

    FileUploadResult toFileUploadResult(ExternalServiceResponse response, int attemptCount) {
        return FileUploadResult.builder()
                .status(response.getStatus())
                .message(response.getMessage())
                .correlationId(response.getCorrelationId())
                .processedAt(response.getProcessedAt())
                .externalReference(response.getExternalReference())
                .success(response.isSuccess())
                .attemptCount(attemptCount)
                .build();
    }
}
