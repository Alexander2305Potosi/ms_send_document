package com.example.fileprocessor.infrastructure.soap.adapter;

import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.infrastructure.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.soap.exception.SoapCommunicationException;
import com.example.fileprocessor.infrastructure.soap.mapper.SoapMapper;
import com.example.fileprocessor.infrastructure.soap.xml.SoapEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
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
            .responseTimeout(Duration.ofSeconds(properties.timeoutSeconds()));

        this.webClient = webClientBuilder
            .baseUrl(properties.endpoint())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    @Override
    public Mono<SoapResponse> sendFile(SoapRequest request) {
        log.info("Sending SOAP request for traceId: {}, endpoint: {}",
            request.traceId(), properties.endpoint());

        String soapBody = soapMapper.toSoapXml(request);
        String soapEnvelope = SoapEnvelope.wrap(soapBody);

        return webClient.post()
            .contentType(MediaType.TEXT_XML)
            .header("SOAPAction", "http://example.com/fileservice/UploadFile")
            .bodyValue(soapEnvelope)
            .retrieve()
            .onStatus(HttpStatusCode::isError, response ->
                response.bodyToMono(String.class)
                    .flatMap(body -> {
                        log.error("SOAP error response: {}", body);
                        return Mono.error(new SoapCommunicationException(
                            "SOAP service returned error: " + response.statusCode(),
                            mapHttpStatusToCode(response.statusCode()),
                            request.traceId()));
                    }))
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
            .retryWhen(Retry.backoff(properties.retryAttempts(),
                    Duration.ofMillis(properties.retryBackoffMillis()))
                .filter(this::isRetryableException)
                .doBeforeRetry(retrySignal ->
                    log.warn("Retrying SOAP call for traceId={}, attempt {}/{} (backoff={}ms)",
                        request.traceId(),
                        retrySignal.totalRetries() + 1,
                        properties.retryAttempts(),
                        properties.retryBackoffMillis() * (retrySignal.totalRetries() + 1))))
            .map(responseXml -> soapMapper.fromSoapXml(responseXml, request.traceId()))
            .doOnNext(response -> log.info("SOAP response received for traceId={}: correlationId={}",
                request.traceId(), response.correlationId()))
            .onErrorResume(TimeoutException.class, e -> {
                log.error("SOAP timeout for traceId: {} after all retries exhausted", request.traceId());
                return Mono.error(new SoapCommunicationException(
                    "SOAP request timed out after " + properties.retryAttempts() + " retries",
                    "GATEWAY_TIMEOUT", request.traceId()));
            })
            .onErrorResume(WebClientResponseException.class, e -> {
                log.error("WebClient error for traceId: {}: {}", request.traceId(), e.getMessage());
                return Mono.error(new SoapCommunicationException(
                    "Communication error with SOAP service: " + e.getMessage(),
                    mapHttpStatusToCode(e.getStatusCode()), request.traceId()));
            });
    }

    private boolean isRetryableException(Throwable throwable) {
        return switch (throwable) {
            case TimeoutException e -> {
                log.debug("Retrying due to timeout");
                yield true;
            }
            case WebClientResponseException e when e.getStatusCode().is5xxServerError() || e.getStatusCode().value() == 503 -> {
                log.debug("Retrying due to server error: {}", e.getStatusCode());
                yield true;
            }
            default -> false;
        };
    }

    private String mapHttpStatusToCode(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 504 -> "GATEWAY_TIMEOUT";
            case Integer i when statusCode.is5xxServerError() -> "BAD_GATEWAY";
            case Integer i when statusCode.is4xxClientError() -> "CLIENT_ERROR";
            default -> "UNKNOWN_ERROR";
        };
    }
}
