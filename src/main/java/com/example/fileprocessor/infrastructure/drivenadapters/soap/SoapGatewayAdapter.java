package com.example.fileprocessor.infrastructure.drivenadapters.soap;

import com.example.fileprocessor.domain.entity.DocumentSendRequest;
import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.domain.port.out.FileGateway;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.drivenadapters.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.exception.SoapCommunicationException;
import com.example.fileprocessor.infrastructure.helpers.soap.mapper.SoapMapper;
import com.example.fileprocessor.infrastructure.helpers.soap.xml.SoapNamespaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

@Component
public class SoapGatewayAdapter implements FileGateway {

    private static final Logger log = LoggerFactory.getLogger(SoapGatewayAdapter.class);
    private static final int MAX_ERROR_BODY_LENGTH = 500;

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
    public Mono<FileUploadResult> send(DocumentSendRequest request) {
        log.info("Sending SOAP request for traceId: {}, endpoint: {}",
            request.getTraceId(), properties.endpoint());

        String soapEnvelope = soapMapper.toFullSoapMessage(request);

        Mono<SoapResponse> soapCall = webClient.post()
            .contentType(MediaType.TEXT_XML)
            .header("SOAPAction", SoapNamespaces.FILE_SERVICE + SoapNamespaces.SOAP_ACTION_UPLOAD)
            .bodyValue(soapEnvelope)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> {
                        String truncatedBody = truncateBody(body);
                        log.error("SOAP error response for traceId={}: {}", request.getTraceId(), truncatedBody);
                        return Mono.error(new SoapCommunicationException(
                            "SOAP error " + response.statusCode() + ": " + truncatedBody,
                            mapHttpStatusToCode(response.statusCode()),
                            request.getTraceId()));
                    }))
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
            .retryWhen(Retry.backoff(properties.retryAttempts(),
                    Duration.ofMillis(properties.retryBackoffMillis()))
                .filter(this::isRetryableException)
                .doBeforeRetry(retrySignal -> {
                    long attempt = retrySignal.totalRetries() + 1;
                    log.warn("Retrying SOAP call for traceId={}, attempt {}/{} (backoff={}ms)",
                        request.getTraceId(),
                        attempt,
                        properties.retryAttempts(),
                        properties.retryBackoffMillis() * attempt);
                }))
            .map(responseXml -> soapMapper.fromSoapXml(responseXml, request.getTraceId()))
            .doOnNext(response -> log.info("SOAP response received for traceId={}: correlationId={}",
                request.getTraceId(), response.getCorrelationId()));

        return soapCall
            .map(response -> toFileUploadResult(response, request.getTraceId()))
            .onErrorResume(throwable -> {
                int retries = 0;
                Throwable cause = throwable;
                if (Exceptions.isRetryExhausted(throwable)) {
                    cause = Exceptions.unwrap(throwable);
                    retries = properties.retryAttempts();
                }
                if (cause instanceof TimeoutException) {
                    log.error("SOAP timeout for traceId: {} after {} retries", request.getTraceId(), retries);
                    return Mono.just(toFileUploadResultError(
                        "SOAP request timed out after " + retries + " retries",
                        ProcessingResultCodes.GATEWAY_TIMEOUT, request.getTraceId()));
                }
                if (cause instanceof WebClientResponseException e) {
                    log.error("WebClient error for traceId: {}: {}", request.getTraceId(), e.getMessage());
                    return Mono.just(toFileUploadResultError(
                        "Communication error with SOAP service: " + e.getMessage(),
                        mapHttpStatusToCode(e.getStatusCode()), request.getTraceId()));
                }
                if (cause instanceof ConnectException) {
                    log.error("Connection failed for traceId: {}: {}", request.getTraceId(), cause.getMessage());
                    return Mono.just(toFileUploadResultError(
                        "Connection failed: " + cause.getMessage(),
                        ProcessingResultCodes.UNKNOWN_ERROR, request.getTraceId()));
                }
                if (cause instanceof IOException) {
                    log.error("IO error for traceId: {}: {}", request.getTraceId(), cause.getMessage());
                    return Mono.just(toFileUploadResultError(
                        "IO error: " + cause.getMessage(),
                        ProcessingResultCodes.UNKNOWN_ERROR, request.getTraceId()));
                }
                return Mono.error(throwable);
            });
    }

    private FileUploadResult toFileUploadResult(SoapResponse response, String traceId) {
        return FileUploadResult.builder()
            .status(response.getStatus())
            .message(response.getMessage())
            .correlationId(response.getCorrelationId())
            .traceId(traceId)
            .processedAt(response.getProcessedAt())
            .externalReference(response.getExternalReference())
            .success(response.isSuccess())
            .build();
    }

    private FileUploadResult toFileUploadResultError(String message, String errorCode, String traceId) {
        return FileUploadResult.builder()
            .status(DocumentStatus.FAILURE_VALUE)
            .message(message)
            .traceId(traceId)
            .processedAt(Instant.now())
            .success(false)
            .build();
    }

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof TimeoutException) {
            return true;
        }
        if (throwable instanceof ConnectException) {
            return true;
        }
        if (throwable instanceof IOException) {
            return true;
        }
        if (throwable instanceof WebClientResponseException e) {
            int statusCode = e.getStatusCode().value();
            if (statusCode == 429) {
                return true;
            }
            if (e.getStatusCode().is5xxServerError()) {
                return true;
            }
        }
        return false;
    }

    private String mapHttpStatusToCode(HttpStatusCode statusCode) {
        int value = statusCode.value();
        if (value == 504) {
            return ProcessingResultCodes.GATEWAY_TIMEOUT;
        }
        if (statusCode.is5xxServerError()) {
            return ProcessingResultCodes.BAD_GATEWAY;
        }
        if (statusCode.is4xxClientError()) {
            return ProcessingResultCodes.CLIENT_ERROR;
        }
        return ProcessingResultCodes.UNKNOWN_ERROR;
    }

    private String truncateBody(String body) {
        if (body == null) return "null";
        if (body.length() <= MAX_ERROR_BODY_LENGTH) return body;
        return body.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
    }
}
