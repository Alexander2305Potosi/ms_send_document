package com.example.fileprocessor.infrastructure.helpers.soap.v2.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "app.soap.v2")
public record SoapV2Properties(
    @NotBlank String endpoint,
    @NotBlank String systemId,
    @NotBlank String userName,
    @NotBlank String headerNamespace,
    @NotBlank String bodyNamespace,
    @NotBlank String subTipoDocumental,

    String userToken,
    String destinationName,
    String destinationNamespace,
    String destinationOperation,
    String soapAction,

    List<String> classifications,
    Map<String, String> messageContext,
    Map<String, String> metaData,

    @Min(1) int timeoutSeconds,
    @Min(1) int retryAttempts
) {
    public SoapV2Properties {
        if (classifications == null) classifications = List.of();
        if (messageContext == null) messageContext = Map.of();
        if (metaData == null) metaData = Map.of();
        if (timeoutSeconds <= 0) timeoutSeconds = 30;
        if (retryAttempts <= 0) retryAttempts = 1;
    }
}
