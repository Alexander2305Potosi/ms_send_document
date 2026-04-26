package com.example.fileprocessor.infrastructure.entrypoints.rest.config;

import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.RestApiPaths;
import com.example.fileprocessor.infrastructure.entrypoints.rest.handler.ProductHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class ProductRoutes {

    @Bean
    public RouterFunction<ServerResponse> productRouter(ProductHandler handler) {
        return route()
            .GET(RestApiPaths.API_V1_PRODUCTS_LOAD, handler::loadProducts)
            .GET(RestApiPaths.API_V1_PRODUCTS, handler::processPendingProducts)
            .build();
    }
}