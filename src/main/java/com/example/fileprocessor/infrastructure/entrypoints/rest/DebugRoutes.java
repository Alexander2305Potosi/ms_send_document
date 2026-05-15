package com.example.fileprocessor.infrastructure.entrypoints.rest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * Independent debug routes for E2E validation.
 * Only active in 'dev' profile.
 * Directly queries the database without touching domain logic.
 */
@Configuration
@Profile("dev")
public class DebugRoutes {

    private final DatabaseClient databaseClient;
    private final DatabaseClient masterDatabaseClient;

    public DebugRoutes(DatabaseClient databaseClient, 
                       @Qualifier("masterDatabaseClient") DatabaseClient masterDatabaseClient) {
        this.databaseClient = databaseClient;
        this.masterDatabaseClient = masterDatabaseClient;
    }

    @Bean
    public RouterFunction<ServerResponse> debugRouter() {
        return route()
            .GET("/api/v1/debug/db/dump", request -> dumpAllTables())
            .build();
    }

    private Mono<ServerResponse> dumpAllTables() {
        return Mono.zip(
            dumpTable(databaseClient, "documentos"),
            dumpTable(databaseClient, "historico_documentos"),
            dumpTable(masterDatabaseClient, "productos_maestros")
        ).flatMap(tuple -> ServerResponse.ok().bodyValue(Map.of(
            "documentos", tuple.getT1(),
            "historico_documentos", tuple.getT2(),
            "productos_maestros", tuple.getT3()
        )));
    }

    private Mono<?> dumpTable(DatabaseClient client, String tableName) {
        return client.sql("SELECT * FROM " + tableName)
            .fetch()
            .all()
            .collectList();
    }
}
