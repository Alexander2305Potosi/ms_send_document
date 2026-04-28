package com.example.fileprocessor.infrastructure.helpers.config;

import com.example.fileprocessor.domain.port.out.ResilienceOperator;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(CircuitBreakerProperties.class)
public class CircuitBreakerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerConfiguration.class);

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

    @Bean
    public ResilienceOperator soapResilienceOperator(CircuitBreaker soapCircuitBreaker) {
        return (source, operationName) -> source
            .transformDeferred(CircuitBreakerOperator.of(soapCircuitBreaker))
            .onErrorResume(CallNotPermittedException.class, e -> {
                log.warn("Circuit breaker OPEN for operation: {}", operationName);
                return Mono.error(new com.example.fileprocessor.domain.exception.CommunicationException(
                    "Circuit breaker is OPEN",
                    "CIRCUIT_BREAKER_OPEN",
                    operationName, 0));
            });
    }

    @Bean
    public ResilienceOperator s3ResilienceOperator(CircuitBreaker s3CircuitBreaker) {
        return (source, operationName) -> source
            .transformDeferred(CircuitBreakerOperator.of(s3CircuitBreaker))
            .onErrorResume(CallNotPermittedException.class, e -> {
                log.warn("Circuit breaker OPEN for operation: {}", operationName);
                return Mono.error(new com.example.fileprocessor.domain.exception.CommunicationException(
                    "Circuit breaker is OPEN",
                    "CIRCUIT_BREAKER_OPEN",
                    operationName, 0));
            });
    }
}