package com.example.fileprocessor.infrastructure.rest.config;

import com.example.fileprocessor.infrastructure.rest.handler.ProductHandler;
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
            .GET("/api/v1/products/load", handler::loadProducts)
            .GET("/api/v1/products", handler::processPendingProducts)
            .build();
    }
}