package com.example.fileprocessor.infrastructure.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class DbInitializer {

    private final ConnectionFactory connectionFactory;
    
    @Qualifier("masterConnectionFactory")
    private final ConnectionFactory masterConnectionFactory;

    @PostConstruct
    public void init() {
        System.out.println("DEBUG: Executing DB Initializer...");
        
        // Local DB
        var localInitializer = new ConnectionFactoryInitializer();
        localInitializer.setConnectionFactory(connectionFactory);
        localInitializer.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("schema.sql")));
        localInitializer.afterPropertiesSet();
        
        // Master DB
        var masterInitializer = new ConnectionFactoryInitializer();
        masterInitializer.setConnectionFactory(masterConnectionFactory);
        masterInitializer.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("master-schema.sql")));
        masterInitializer.afterPropertiesSet();
        
        System.out.println("DEBUG: DB Initializer completed.");
    }
}
