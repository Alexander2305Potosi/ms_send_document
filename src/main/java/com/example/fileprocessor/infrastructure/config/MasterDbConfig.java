package com.example.fileprocessor.infrastructure.config;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.core.DatabaseClient;

@Configuration
@EnableR2dbcRepositories(
    basePackages = "com.example.fileprocessor.infrastructure.drivenadapters.masterdb.repository",
    entityOperationsRef = "masterEntityTemplate"
)
public class MasterDbConfig {

    @Value("${spring.master-r2dbc.url}")
    private String masterUrl;

    @Value("${spring.master-r2dbc.username}")
    private String username;

    @Value("${spring.master-r2dbc.password:}")
    private String password;

    @Bean(name = "masterConnectionFactory")
    public ConnectionFactory masterConnectionFactory() {
        return ConnectionFactories.get(masterUrl);
    }

    @Bean(name = "masterDatabaseClient")
    public DatabaseClient masterDatabaseClient(ConnectionFactory masterConnectionFactory) {
        return DatabaseClient.create(masterConnectionFactory);
    }

    @Bean(name = "masterEntityTemplate")
    public R2dbcEntityTemplate masterEntityTemplate(ConnectionFactory masterConnectionFactory) {
        return new R2dbcEntityTemplate(masterConnectionFactory);
    }
}
