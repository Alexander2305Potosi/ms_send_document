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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

            String soapEnvelope = soapMapper.toFullSoapMessage(request);

            return webClient.post()
                    .contentType(MediaType.TEXT_XML)
                    .header("SOAPAction", SoapConstants.FILE_SERVICE + SoapConstants.SOAP_ACTION_UPLOAD)
                    .bodyValue(soapEnvelope)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .map(soapMapper::fromSoapXml)
                    .doOnNext(response -> log.info("SOAP response received for traceId={}: correlationId={}",
                            traceId, response.getCorrelationId()))
                    .map(this::toFileUploadResult)
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.error("SOAP HTTP error for traceId={}: {} {}", traceId, ex.getStatusCode(), ex.getMessage());
                        return Mono.just(buildErrorResult(traceId, SoapErrorCodes.BAD_GATEWAY, ex.getMessage()));
                    })
                    .onErrorResume(TimeoutException.class, ex -> {
                        log.error("SOAP timeout for traceId={}", traceId);
                        return Mono.just(buildErrorResult(traceId, SoapErrorCodes.GATEWAY_TIMEOUT, "Timeout after " + properties.timeoutSeconds() + "s"));
                    })
                    .onErrorResume(IOException.class, ex -> {
                        log.error("SOAP IO error for traceId={}: {}", traceId, ex.getMessage());
                        return Mono.just(buildErrorResult(traceId, SoapErrorCodes.UNKNOWN_ERROR, ex.getMessage()));
                    })
                    .onErrorResume(ConnectException.class, ex -> {
                        log.error("SOAP connection error for traceId={}: {}", traceId, ex.getMessage());
                        return Mono.just(buildErrorResult(traceId, SoapErrorCodes.SERVICE_UNAVAILABLE, ex.getMessage()));
                    });
        });
    }

    private FileUploadResult buildErrorResult(String traceId, String errorCode, String message) {
        return FileUploadResult.builder()
                .status(DocumentStatus.FAILURE.name())
                .errorCode(errorCode)
                .traceId(traceId)
                .message(message)
                .processedAt(Instant.now())
                .success(false)
                .build();
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
}
