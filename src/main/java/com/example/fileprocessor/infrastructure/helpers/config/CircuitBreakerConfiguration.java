package com.example.fileprocessor.infrastructure.helpers.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(CircuitBreakerProperties.class)
public class CircuitBreakerConfiguration {

    private final CircuitBreakerProperties properties;

    public CircuitBreakerConfiguration(CircuitBreakerProperties properties) {
        this.properties = properties;
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config =
            io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
            .failureRateThreshold(properties.failureRateThreshold())
            .waitDurationInOpenState(Duration.ofMillis(properties.waitDurationInOpenStateMillis()))
            .permittedNumberOfCallsInHalfOpenState(properties.permittedNumberOfCallsInHalfOpenState())
            .slidingWindowSize(properties.slidingWindowSize())
            .minimumNumberOfCalls(properties.minimumNumberOfCalls())
            .build();

        return CircuitBreakerRegistry.of(config);
    }

    @Bean
    public CircuitBreaker soapCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("SOAP");
    }

    @Bean
    public CircuitBreaker s3CircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("S3");
    }
}