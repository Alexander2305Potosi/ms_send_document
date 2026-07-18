package com.example.fileprocessor.infrastructure.drivenadapters.masterdb;

import com.example.fileprocessor.domain.entity.product.maestro.ProductMaestro;
import com.example.fileprocessor.infrastructure.drivenadapters.masterdb.entity.ProductMasterEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.masterdb.repository.ProductMasterR2dbcRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductMasterR2dbcAdapterTest {

    private ProductMasterR2dbcRepository repository;
    private ProductMasterR2dbcAdapter adapter;

    @BeforeEach
    void setUp() {
        repository = mock(ProductMasterR2dbcRepository.class);
        adapter = new ProductMasterR2dbcAdapter(repository);
    }

    @Test
    void testGetAllProductsWithoutContextFilters() {
        ProductMasterEntity entity = new ProductMasterEntity(1L, "p1", "Product 1", LocalDateTime.now(), "SYNCED", "folder", "US");
        when(repository.findAllProducts(null, null, null, null)).thenReturn(Flux.just(entity));

        StepVerifier.create(adapter.getAllProducts())
                .assertNext(product -> {
                    assertEquals(1L, product.getId());
                    assertEquals("p1", product.getProductId());
                    assertEquals("Product 1", product.getName());
                    assertEquals("SYNCED", product.getState());
                    assertEquals("folder", product.getOriginFolder());
                    assertEquals("US", product.getOriginCountry());
                })
                .expectComplete()
                .verify();

        verify(repository, times(1)).findAllProducts(null, null, null, null);
    }

    @Test
    void testGetAllProductsWithContextFilters() {
        ProductMasterEntity entity = new ProductMasterEntity(1L, "p1", "Product 1", LocalDateTime.now(), "SYNCED", "folder", "US");
        when(repository.findAllProducts(eq("SYNCED"), any(), any(), eq("last-123"))).thenReturn(Flux.just(entity));

        StepVerifier.create(adapter.getAllProducts()
                .contextWrite(Context.of(
                        ApiConstants.HEADER_DATE_INIT, "2026-01-01",
                        ApiConstants.HEADER_DATE_END, "2026-01-10",
                        ApiConstants.HEADER_PRODUCT_STATUS, "SYNCED",
                        ApiConstants.LAST_PRODUCT_ID, "last-123"
                )))
                .assertNext(product -> {
                    assertEquals("p1", product.getProductId());
                })
                .expectComplete()
                .verify();

        verify(repository, times(1)).findAllProducts(eq("SYNCED"), any(), any(), eq("last-123"));
    }

    @Test
    void testCountAllProductsWithContextFilters() {
        when(repository.countAllProducts(eq("SYNCED"), any(), any(), eq("last-123"))).thenReturn(Mono.just(5L));

        StepVerifier.create(adapter.countAllProducts()
                .contextWrite(Context.of(
                        ApiConstants.HEADER_DATE_INIT, "2026-01-01",
                        ApiConstants.HEADER_DATE_END, "2026-01-10",
                        ApiConstants.HEADER_PRODUCT_STATUS, "SYNCED",
                        ApiConstants.LAST_PRODUCT_ID, "last-123"
                )))
                .expectNext(5L)
                .expectComplete()
                .verify();

        verify(repository, times(1)).countAllProducts(eq("SYNCED"), any(), any(), eq("last-123"));
    }
}
