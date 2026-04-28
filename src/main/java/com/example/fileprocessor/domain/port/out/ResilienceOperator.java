package com.example.fileprocessor.domain.port.out;

import reactor.core.publisher.Mono;

/**
 * Abstracts resilience patterns (circuit breaker, retry, etc.) from domain logic.
 * Implementations use infrastructure-specific libraries like Resilience4j.
 */
@FunctionalInterface
public interface ResilienceOperator {
    Mono<?> decorate(Mono<?> source, String operationName);
}
