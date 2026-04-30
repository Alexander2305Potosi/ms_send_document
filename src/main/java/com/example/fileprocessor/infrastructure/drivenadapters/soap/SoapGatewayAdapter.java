package com.example.fileprocessor.infrastructure.drivenadapters.soap;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.ExternalServiceResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.drivenadapters.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import com.example.fileprocessor.infrastructure.helpers.soap.SoapConstants;
import com.example.fileprocessor.infrastructure.helpers.soap.mapper.SoapMapper;
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

/**
 * SOAP gateway adapter for file upload operations.
 */
@Component
public class SoapGatewayAdapter implements SoapGateway {

    private static final Logger log = LoggerFactory.getLogger(SoapGatewayAdapter.class);

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
            log.info("Sending SOAP request for traceId: {}, endpoint: {}", traceId, properties.endpoint());

            String soapEnvelope = soapMapper.toFullSoapMessage(
                request.documentId(), request.content(), request.filename(),
                request.contentType(), request.fileSize(),
                request.parentFolder(), request.childFolder());

            Mono<ExternalServiceResponse> soapCall = webClient.post()
                .contentType(MediaType.TEXT_XML)
                .header("SOAPAction", SoapConstants.FILE_SERVICE + SoapConstants.SOAP_ACTION_UPLOAD)
                .bodyValue(soapEnvelope)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                    response.bodyToMono(String.class)
                        .flatMap(body -> {
                            String truncatedBody = truncateBody(body);
                            log.error("SOAP error response for traceId={}: {}", traceId, truncatedBody);
                            return Mono.error(ProcessingException.fromContext(ctx,
                                "SOAP error " + response.statusCode() + ": " + truncatedBody,
                                mapHttpStatusToCode(response.statusCode()), request.documentId()));
                        }))
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .retryWhen(Retry.backoff(properties.retryAttempts(),
                        Duration.ofMillis(properties.retryBackoffMillis()))
                    .filter(this::isRetryableException)
                    .doBeforeRetry(retrySignal -> {
                        long attempt = retrySignal.totalRetries() + 1;
                        log.warn("Retrying SOAP call for traceId={}, attempt {}/{} (backoff={}ms)",
                            traceId, attempt, properties.retryAttempts(),
                            properties.retryBackoffMillis() * attempt);
                    }))
                .map(soapMapper::fromSoapXml)
                .doOnNext(response -> log.info("SOAP response received for traceId={}: correlationId={}",
                    traceId, response.getCorrelationId()));

            return soapCall
                .map(response -> toFileUploadResult(response))
                .onErrorResume(throwable -> {
                    int retries = 0;
                    Throwable cause = throwable;
                    if (Exceptions.isRetryExhausted(throwable)) {
                        cause = Exceptions.unwrap(throwable);
                        retries = properties.retryAttempts();
                    }
                    if (cause instanceof TimeoutException) {
                        log.error("SOAP timeout for traceId: {} after {} retries", traceId, retries);
                        return Mono.error(ProcessingException.fromContext(ctx,
                            "SOAP request timed out after " + retries + " retries",
                            ProcessingResultCodes.GATEWAY_TIMEOUT, request.documentId()));
                    }
                    if (cause instanceof WebClientResponseException e) {
                        if (e.getStatusCode().is5xxServerError()) {
                            log.error("SOAP server error for traceId: {}: {}", traceId, e.getMessage());
                            return Mono.error(ProcessingException.fromContext(ctx,
                                "SOAP service error: " + e.getMessage(),
                                mapHttpStatusToCode(e.getStatusCode()), request.documentId()));
                        }
                        log.error("SOAP client error for traceId: {}: {}", traceId, e.getMessage());
                        return Mono.just(toFileUploadResultError(
                            "Communication error with SOAP service: " + e.getMessage(),
                            mapHttpStatusToCode(e.getStatusCode())));
                    }
                    if (cause instanceof ConnectException) {
                        log.error("Connection failed for traceId: {}: {}", traceId, cause.getMessage());
                        return Mono.error(ProcessingException.fromContext(ctx,
                            "Connection failed: " + cause.getMessage(),
                            ProcessingResultCodes.UNKNOWN_ERROR, request.documentId()));
                    }
                    if (cause instanceof IOException) {
                        log.error("IO error for traceId: {}: {}", traceId, cause.getMessage());
                        return Mono.error(ProcessingException.fromContext(ctx,
                            "IO error: " + cause.getMessage(),
                            ProcessingResultCodes.UNKNOWN_ERROR, request.documentId()));
                    }
                    return Mono.error(throwable);
                });
        });
    }

    private FileUploadResult toFileUploadResult(ExternalServiceResponse response) {
        return FileUploadResult.builder()
            .status(response.getStatus())
            .message(response.getMessage())
            .correlationId(response.getCorrelationId())
            .processedAt(response.getProcessedAt())
            .externalReference(response.getExternalReference())
            .success(response.isSuccess())
            .build();
    }

    private FileUploadResult toFileUploadResultError(String message, String errorCode) {
        return FileUploadResult.builder()
            .status(DocumentStatus.FAILURE.name())
            .message(message)
            .errorCode(errorCode)
            .processedAt(Instant.now())
            .success(false)
            .build();
    }

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof TimeoutException) return true;
        if (throwable instanceof ConnectException) return true;
        if (throwable instanceof IOException) return true;
        if (throwable instanceof WebClientResponseException e) {
            int statusCode = e.getStatusCode().value();
            return statusCode == 429 || e.getStatusCode().is5xxServerError();
        }
        return false;
    }

    private String mapHttpStatusToCode(HttpStatusCode statusCode) {
        int value = statusCode.value();
        if (value == 504) return ProcessingResultCodes.GATEWAY_TIMEOUT;
        if (statusCode.is5xxServerError()) return ProcessingResultCodes.BAD_GATEWAY;
        if (statusCode.is4xxClientError()) return ProcessingResultCodes.CLIENT_ERROR;
        return ProcessingResultCodes.UNKNOWN_ERROR;
    }

    private String truncateBody(String body) {
        if (body == null) return "null";
        if (body.length() <= properties.maxErrorBodyLength()) return body;
        return body.substring(0, properties.maxErrorBodyLength()) + "...";
    }
}
