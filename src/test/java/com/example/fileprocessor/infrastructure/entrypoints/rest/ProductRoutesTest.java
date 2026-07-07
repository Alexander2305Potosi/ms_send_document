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
import org.springframework.http.MediaType;
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
                                "/products/{type_job}",
                                "/products/sync/{type_job}",
                                "/products/sync/status/{type_job}",
                                "/products/process/status/{type_job}",
                                "/products/daily/animal",
                                "/products/process/status/daily/animal");
                ProductRoutes productRoutes = new ProductRoutes(pathProperties);

                RouterFunction<ServerResponse> combinedRouter = productRoutes.processPendingProducts(productHandler)
                                .and(productRoutes.syncProducts(productHandler))
                                .and(productRoutes.syncStatusRoute(productHandler))
                                .and(productRoutes.processStatusRoute(productHandler));

                client = WebTestClient.bindToRouterFunction(combinedRouter).build();
        }

        @Test
        void processPendingProductsRouteShouldRouteToHandler() {
                when(productHandler.processPendingProducts(any(ServerRequest.class)))
                                .thenReturn(ServerResponse.ok().build());

                client.get()
                                .uri("/api/v1/products/soap")
                                .exchange()
                                .expectStatus().isOk();

                verify(productHandler).processPendingProducts(any(ServerRequest.class));
        }

        @Test
        void syncProductsRouteShouldRouteToHandler() {
                Mono<ServerResponse> mockResponse = ServerResponse.accepted()
                                .contentType(MediaType.TEXT_PLAIN)
                                .bodyValue("1");
                when(productHandler.syncProducts(any(ServerRequest.class)))
                                .thenReturn(mockResponse);

                client.get()
                                .uri("/api/v1/products/sync/retention")
                                .exchange()
                                .expectStatus().isAccepted()
                                .expectBody(String.class).isEqualTo("1");

                verify(productHandler).syncProducts(any(ServerRequest.class));
        }

        @Test
        void syncStatusRouteShouldRouteToHandler() {
                Mono<ServerResponse> mockResponse = ServerResponse.ok().bodyValue("exitoso");
                when(productHandler.getSyncStatus(any(ServerRequest.class)))
                                .thenReturn(mockResponse);

                client.get()
                                .uri("/api/v1/products/sync/status/retention")
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(String.class).isEqualTo("exitoso");

                verify(productHandler).getSyncStatus(any(ServerRequest.class));
        }

        @Test
        void processStatusRouteShouldRouteToHandler() {
                Mono<ServerResponse> mockResponse = ServerResponse.ok().bodyValue("1");
                when(productHandler.getProcessStatus(any(ServerRequest.class)))
                                .thenReturn(mockResponse);

                client.get()
                                .uri("/api/v1/products/process/status/retention")
                                .exchange()
                                .expectStatus().isOk()
                                .expectBody(String.class).isEqualTo("1");

                verify(productHandler).getProcessStatus(any(ServerRequest.class));
        }
}
