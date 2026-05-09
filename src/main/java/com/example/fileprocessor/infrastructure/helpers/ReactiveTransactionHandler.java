package com.example.fileprocessor.infrastructure.helpers;

import com.example.fileprocessor.domain.port.out.TransactionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

/**
 * Infrastructure implementation of TransactionHandler using Spring's TransactionalOperator.
 */
@Component
@RequiredArgsConstructor
public class ReactiveTransactionHandler implements TransactionHandler {

    private final TransactionalOperator transactionalOperator;

    @Override
    public <T> Mono<T> run(Mono<T> publisher) {
        return publisher.as(transactionalOperator::transactional);
    }
}
