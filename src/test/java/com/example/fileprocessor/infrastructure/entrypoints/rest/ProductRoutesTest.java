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
        PathProperties pathProperties = new PathProperties(
                "/api/v1",
                "/products",
                "/products/sync",
                "/products/sync/status/{type_job}",
                "/products/process/status/{type_job}"
        );
        ProductRoutes productRoutes = new ProductRoutes(pathProperties);

        RouterFunction<ServerResponse> combinedRouter = productRoutes.processPendingProducts(productHandler)
                .and(productRoutes.syncProducts(productHandler))
                .and(productRoutes.syncStatusRoute(productHandler))
                .and(productRoutes.processStatusRoute(productHandler));

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

    @Test
    void syncStatusRoute_shouldRouteToHandler() {
        when(productHandler.getSyncStatus(any(ServerRequest.class)))
                .thenReturn(ServerResponse.ok().bodyValue("exitoso"));

        client.get()
                .uri("/api/v1/products/sync/status/retention")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("exitoso");

        verify(productHandler).getSyncStatus(any(ServerRequest.class));
    }

    @Test
    void processStatusRoute_shouldRouteToHandler() {
        when(productHandler.getProcessStatus(any(ServerRequest.class)))
                .thenReturn(ServerResponse.ok().bodyValue("1"));

        client.get()
                .uri("/api/v1/products/process/status/retention")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("1");

        verify(productHandler).getProcessStatus(any(ServerRequest.class));
    }
}
