package com.example.fileprocessor.infrastructure.soap.adapter;

import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.domain.usecase.DocumentErrorCodes;
import com.example.fileprocessor.infrastructure.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.soap.exception.SoapCommunicationException;
import com.example.fileprocessor.infrastructure.soap.mapper.SoapMapper;
import com.example.fileprocessor.infrastructure.soap.xml.SoapNamespaces;
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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeoutException;

@Component
public class ExternalSoapGatewayImpl implements ExternalSoapGateway {

    private static final Logger log = LoggerFactory.getLogger(ExternalSoapGatewayImpl.class);

    private final WebClient webClient;
    private final SoapProperties properties;
    private final SoapMapper soapMapper;

    public ExternalSoapGatewayImpl(WebClient.Builder webClientBuilder,
                                    SoapProperties properties,
                                    SoapMapper soapMapper) {
        this.properties = properties;
        this.soapMapper = soapMapper;

        HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofSeconds(Math.max(1, properties.timeoutSeconds() - 5)));

        this.webClient = webClientBuilder
            .baseUrl(properties.endpoint())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    @Override
    public Mono<SoapResponse> sendFile(SoapRequest request) {
        log.info("Sending SOAP request for traceId: {}, endpoint: {}",
            request.getTraceId(), properties.endpoint());

        String soapEnvelope = soapMapper.toFullSoapMessage(request);
        AtomicInteger retryCount = new AtomicInteger(0);

        Mono<SoapResponse> soapCall = webClient.post()
            .contentType(MediaType.TEXT_XML)
            .header("SOAPAction", SoapNamespaces.FILE_SERVICE + "/UploadFile")
            .bodyValue(soapEnvelope)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("SOAP error response: {}", body);
                        return Mono.error(new SoapCommunicationException(
                            "SOAP service returned error: " + response.statusCode(),
                            mapHttpStatusToCode(response.statusCode()),
                            request.getTraceId()));
                    }))
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
            .retryWhen(Retry.backoff(properties.retryAttempts(),
                    Duration.ofMillis(properties.retryBackoffMillis()))
                .filter(this::isRetryableException)
                .doBeforeRetry(retrySignal -> {
                    int attempts = (int) retrySignal.totalRetries() + 1;
                    retryCount.set(attempts);
                    log.warn("Retrying SOAP call for traceId={}, attempt {}/{} (backoff={}ms)",
                        request.getTraceId(),
                        attempts,
                        properties.retryAttempts(),
                        properties.retryBackoffMillis() * attempts);
                }))
            .map(responseXml -> soapMapper.fromSoapXml(responseXml, request.getTraceId()))
            .doOnNext(response -> log.info("SOAP response received for traceId={}: correlationId={}",
                request.getTraceId(), response.getCorrelationId()));

        return soapCall
            .onErrorResume(throwable -> {
                Throwable cause = throwable;
                if (Exceptions.isRetryExhausted(throwable)) {
                    cause = Exceptions.unwrap(throwable);
                }
                int retries = retryCount.get();
                if (cause instanceof TimeoutException) {
                    log.error("SOAP timeout for traceId: {} after {} retries", request.getTraceId(), retries);
                    return Mono.error(new SoapCommunicationException(
                        "SOAP request timed out after " + properties.retryAttempts() + " retries",
                        DocumentErrorCodes.GATEWAY_TIMEOUT, request.getTraceId(), retries));
                }
                if (cause instanceof WebClientResponseException e) {
                    log.error("WebClient error for traceId: {}: {}", request.getTraceId(), e.getMessage());
                    return Mono.error(new SoapCommunicationException(
                        "Communication error with SOAP service: " + e.getMessage(),
                        mapHttpStatusToCode(e.getStatusCode()), request.getTraceId(), retries));
                }
                return Mono.error(throwable);
            });
    }

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof TimeoutException) {
            log.debug("Retrying due to timeout");
            return true;
        }
        if (throwable instanceof WebClientResponseException e) {
            if (e.getStatusCode().is5xxServerError()) {
                log.debug("Retrying due to server error: {}", e.getStatusCode());
                return true;
            }
        }
        return false;
    }

    private String mapHttpStatusToCode(HttpStatusCode statusCode) {
        int value = statusCode.value();
        if (value == 504) {
            return DocumentErrorCodes.GATEWAY_TIMEOUT;
        }
        if (statusCode.is5xxServerError()) {
            return DocumentErrorCodes.BAD_GATEWAY;
        }
        if (statusCode.is4xxClientError()) {
            return DocumentErrorCodes.CLIENT_ERROR;
        }
        return DocumentErrorCodes.UNKNOWN_ERROR;
    }
}