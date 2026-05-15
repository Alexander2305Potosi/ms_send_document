package com.example.fileprocessor.infrastructure.config;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration
public class R2dbcConfig {

    @Value("${spring.r2dbc.url}")
    private String url;

    @Bean
    @Primary
    public ConnectionFactory connectionFactory() {
        return ConnectionFactories.get(url);
    }

    @Bean
    @Primary
    public DatabaseClient databaseClient(ConnectionFactory connectionFactory) {
        return DatabaseClient.create(connectionFactory);
    }

    @Bean(name = "r2dbcEntityTemplate")
    @Primary
    public R2dbcEntityTemplate r2dbcEntityTemplate(ConnectionFactory connectionFactory) {
        return new R2dbcEntityTemplate(connectionFactory);
    }

    @Bean
    @Primary
    public ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

    @Bean
    @Primary
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }
}
