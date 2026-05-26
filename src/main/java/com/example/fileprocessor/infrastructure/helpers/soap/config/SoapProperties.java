package com.example.fileprocessor.infrastructure.helpers.soap.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "app.soap.v2")
public record SoapProperties(
        @NotBlank String endpoint,
        @NotBlank String systemId,
        @NotBlank String userName,
        @NotBlank String headerNamespace,
        @NotBlank String bodyNamespace,
        @NotBlank String soapNamespace,

        String userToken,
        String destinationName,
        String destinationNamespace,
        String destinationOperation,
        String soapAction,

        String classification,
        Map<String, String> messageContext,
        Map<String, String> metaData,

        @Min(1) int timeoutSeconds,
        @Min(1) int retryAttempts) {
    public SoapProperties {
        if (messageContext == null)
            messageContext = Map.of();
        if (metaData == null)
            metaData = Map.of();
        if (timeoutSeconds <= 0)
            timeoutSeconds = 30;
        if (retryAttempts < 0)
            retryAttempts = 0;
    }
}
