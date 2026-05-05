package com.example.fileprocessor.infrastructure.drivenadapters.soap;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.infrastructure.drivenadapters.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import com.example.fileprocessor.infrastructure.helpers.soap.SoapConstants;
import com.example.fileprocessor.infrastructure.helpers.soap.mapper.SoapMapper;
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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SOAP gateway adapter for file upload operations.
 */
@Component
public class SoapGatewayAdapter implements SoapGateway {

    private static final Logger log = Logger.getLogger(SoapGatewayAdapter.class.getName());

    private final WebClient webClient;
    private final SoapProperties properties;
    private final SoapMapper soapMapper;

    public SoapGatewayAdapter(WebClient.Builder webClientBuilder,
                              SoapProperties properties,
                              SoapMapper soapMapper) {
        this.properties = properties;
        this.soapMapper = soapMapper;

        HttpClient httpClient = HttpClient.create();

        this.webClient = webClientBuilder
            .baseUrl(properties.endpoint())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    @Override
    public Mono<FileUploadResult> send(FileUploadRequest request) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.get(ApiConstants.HEADER_TRACE_ID);
            int maxRetries = properties.retryAttempts();
            AtomicInteger attemptCount = new AtomicInteger(1);

            log.log(Level.INFO, "Sending SOAP request for traceId: {0}, endpoint: {1}, retryAttempts: {2}",
                    new Object[]{traceId, properties.endpoint(), maxRetries});

            String soapEnvelope = soapMapper.toFullSoapMessage(request);

            Mono<ExternalServiceResponse> retryableMono = webClient.post()
                    .contentType(MediaType.TEXT_XML)
                    .header("SOAPAction", SoapConstants.FILE_SERVICE + SoapConstants.SOAP_ACTION_UPLOAD)
                    .bodyValue(soapEnvelope)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .map(soapMapper::fromSoapXml)
                    .doOnNext(response -> log.log(Level.INFO, "SOAP response received for traceId={0}: correlationId={1}",
                            new Object[]{traceId, response.getCorrelationId()}))
                    .doOnError(e -> {
                        int currentAttempt = attemptCount.incrementAndGet();
                        log.log(Level.WARNING, "SOAP request attempt {0}/{1} failed for traceId={2}: {3}",
                                new Object[]{currentAttempt, maxRetries + 1, traceId, e.getMessage()});
                    });

            return retryableMono
                    .retryWhen(reactor.util.retry.Retry.backoff(maxRetries, Duration.ofMillis(500))
                            .filter(this::isRetryable)
                            .doBeforeRetry(signal -> {
                                int currentAttempt = attemptCount.incrementAndGet();
                                log.log(Level.INFO, "Retrying SOAP request for traceId={0}, attempt {1}/{2}",
                                        new Object[]{traceId, currentAttempt, maxRetries + 1});
                            }))
                    .map(response -> toFileUploadResult(response, attemptCount.get()))
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.log(Level.SEVERE, "SOAP HTTP error for traceId={0}: {1} {2}", new Object[]{traceId, ex.getStatusCode(), ex.getMessage()});
                        return Mono.just(buildErrorResult(traceId, SoapErrorCodes.BAD_GATEWAY, ex.getMessage(), attemptCount.get()));
                    })
                    .onErrorResume(TimeoutException.class, ex -> {
                        log.log(Level.SEVERE, "SOAP timeout for traceId={0}", new Object[]{traceId});
                        return Mono.just(buildErrorResult(traceId, SoapErrorCodes.GATEWAY_TIMEOUT, "Timeout after " + properties.timeoutSeconds() + "s", attemptCount.get()));
                    })
                    .onErrorResume(IOException.class, ex -> {
                        log.log(Level.SEVERE, "SOAP IO error for traceId={0}: {1}", new Object[]{traceId, ex.getMessage()});
                        return Mono.just(buildErrorResult(traceId, SoapErrorCodes.UNKNOWN_ERROR, ex.getMessage(), attemptCount.get()));
                    })
                    .onErrorResume(ConnectException.class, ex -> {
                        log.log(Level.SEVERE, "SOAP connection error for traceId={0}: {1}", new Object[]{traceId, ex.getMessage()});
                        return Mono.just(buildErrorResult(traceId, SoapErrorCodes.SERVICE_UNAVAILABLE, ex.getMessage(), attemptCount.get()));
                    })
                    .onErrorResume(Throwable.class, ex -> {
                        log.log(Level.SEVERE, "SOAP unexpected error for traceId={0}: {1}", new Object[]{traceId, ex.getMessage()});
                        return Mono.just(buildErrorResult(traceId, SoapErrorCodes.UNKNOWN_ERROR, ex.getMessage(), attemptCount.get()));
                    });
        });
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