package com.example.fileprocessor.infrastructure.entrypoints.rest;

import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.RestApiPaths;
import com.example.fileprocessor.infrastructure.entrypoints.rest.config.PathProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.handler.ProductHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;
import static org.springframework.web.reactive.function.server.RouterFunctions.nest;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
@RequiredArgsConstructor
public class ProductRoutes {

    private final PathProperties pathProperties;

    @Bean
    public RouterFunction<ServerResponse> processPendingProducts(ProductHandler handler) {
        return nest(
            path(pathProperties.basePath()),
            route(GET(pathProperties.API_V1_PRODUCTS()), handler::processPendingProducts)
            );
    }

    @Bean
    public RouterFunction<ServerResponse> syncProducts(ProductHandler handler) {
        return nest(
                path(pathProperties.basePath()),
                route(GET(pathProperties.API_V1_PRODUCTS_SYNC()), handler::syncProducts)
        );
    }
}