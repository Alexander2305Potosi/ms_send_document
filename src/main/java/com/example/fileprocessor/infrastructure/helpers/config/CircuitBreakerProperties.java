package com.example.fileprocessor.infrastructure.helpers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties(prefix = "app.circuit-breaker")
public record CircuitBreakerProperties(
    @Positive
    int failureRateThreshold,

    @Min(1000)
    long waitDurationInOpenStateMillis,

    @Min(1)
    int permittedNumberOfCallsInHalfOpenState,

    @Min(10)
    int slidingWindowSize,

    @Min(5)
    int minimumNumberOfCalls
) {
    public CircuitBreakerProperties {
        if (failureRateThreshold == 0) {
            failureRateThreshold = 50;
        }
        if (waitDurationInOpenStateMillis == 0) {
            waitDurationInOpenStateMillis = 60000;
        }
        if (permittedNumberOfCallsInHalfOpenState == 0) {
            permittedNumberOfCallsInHalfOpenState = 10;
        }
        if (slidingWindowSize == 0) {
            slidingWindowSize = 100;
        }
        if (minimumNumberOfCalls == 0) {
            minimumNumberOfCalls = 10;
        }
    }
}