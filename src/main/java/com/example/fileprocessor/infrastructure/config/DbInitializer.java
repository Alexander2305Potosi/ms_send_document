package com.example.fileprocessor.infrastructure.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import jakarta.annotation.PostConstruct;

@Configuration
public class DbInitializer {

    private final ConnectionFactory connectionFactory;
    private final ConnectionFactory masterConnectionFactory;
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(DbInitializer.class.getName());

    @org.springframework.beans.factory.annotation.Autowired
    public DbInitializer(
            ConnectionFactory connectionFactory,
            @Qualifier("masterConnectionFactory") ConnectionFactory masterConnectionFactory) {
        this.connectionFactory = connectionFactory;
        this.masterConnectionFactory = masterConnectionFactory;
    }

    @PostConstruct
    public void init() {
        LOGGER.info("Executing DB Initializer...");
        LOGGER.info("connectionFactory metadata: " + connectionFactory.getMetadata().getName());
        LOGGER.info("masterConnectionFactory metadata: " + masterConnectionFactory.getMetadata().getName());
        LOGGER.info("Factories are equal: " + (connectionFactory == masterConnectionFactory));
        
        // Local DB
        ConnectionFactoryInitializer localInitializer = new ConnectionFactoryInitializer();
        localInitializer.setConnectionFactory(connectionFactory);
        localInitializer.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("schema.sql")));
        localInitializer.afterPropertiesSet();
        
        // Master DB
        ConnectionFactoryInitializer masterInitializer = new ConnectionFactoryInitializer();
        masterInitializer.setConnectionFactory(masterConnectionFactory);
        masterInitializer.setDatabasePopulator(new ResourceDatabasePopulator(new ClassPathResource("master-schema.sql")));
        masterInitializer.afterPropertiesSet();
        
        org.springframework.r2dbc.core.DatabaseClient.create(masterConnectionFactory)
            .sql("SELECT COUNT(*) FROM productos_maestros")
            .map(row -> row.get(0, Long.class))
            .one()
            .doOnNext(count -> LOGGER.info("DIAGNOSTIC: productos_maestros table count = " + count))
            .doOnError(err -> LOGGER.log(java.util.logging.Level.SEVERE, "DIAGNOSTIC: Failed to query productos_maestros", err))
            .subscribe();
        
        LOGGER.info("DB Initializer completed.");
    }
}
