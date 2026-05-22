package com.example.fileprocessor.infrastructure.entrypoints.rest;

import com.example.fileprocessor.infrastructure.entrypoints.rest.config.PathProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.handler.ProductHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductRoutesTest {

    @Mock
    private ProductHandler productHandler;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        PathProperties pathProperties = new PathProperties("/api/v1", "/products", "/products/sync");
        ProductRoutes productRoutes = new ProductRoutes(pathProperties);

        RouterFunction<ServerResponse> combinedRouter = productRoutes.processPendingProducts(productHandler)
                .and(productRoutes.syncProducts(productHandler));

        client = WebTestClient.bindToRouterFunction(combinedRouter).build();
    }

    @Test
    void processPendingProductsRoute_shouldRouteToHandler() {
        when(productHandler.processPendingProducts(any(ServerRequest.class)))
                .thenReturn(ServerResponse.ok().build());

        client.get()
                .uri("/api/v1/products")
                .exchange()
                .expectStatus().isOk();

        verify(productHandler).processPendingProducts(any(ServerRequest.class));
    }

    @Test
    void syncProductsRoute_shouldRouteToHandler() {
        when(productHandler.syncProducts(any(ServerRequest.class)))
                .thenReturn(ServerResponse.accepted().build());

        client.get()
                .uri("/api/v1/products/sync")
                .exchange()
                .expectStatus().isAccepted();

        verify(productHandler).syncProducts(any(ServerRequest.class));
    }
}
