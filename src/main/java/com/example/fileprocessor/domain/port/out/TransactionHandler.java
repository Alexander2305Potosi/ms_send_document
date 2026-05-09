package com.example.fileprocessor.domain.port.out;

import reactor.core.publisher.Mono;

/**
 * Port for managing transactions in a reactive way.
 * This decouples the domain from Spring's TransactionalOperator.
 */
public interface TransactionHandler {
    /**
     * Executes the given publisher within a transaction.
     * @param <T> The type of the result.
     * @param publisher The reactive stream to be executed.
     * @return A publisher that executes within a transaction.
     */
    <T> Mono<T> run(Mono<T> publisher);
}
