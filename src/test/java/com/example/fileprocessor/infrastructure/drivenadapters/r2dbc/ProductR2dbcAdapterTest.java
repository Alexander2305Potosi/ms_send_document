package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.ProductHistory;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.ProductEntity;
import org.reactivecommons.utils.ObjectMapper;
import org.reactivecommons.utils.ObjectMapperImp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductR2dbcAdapterTest {

    @Mock
    private com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.ProductRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapperImp();
    private ProductR2dbcAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ProductR2dbcAdapter(repository, objectMapper);
    }

    private static ProductEntity entity(Long id, String productId, String state) {
        return ProductEntity.builder()
            .id(id)
            .productId(productId)
            .name("Product-" + productId)
            .loadDate(LocalDateTime.now())
            .state(state)
            .build();
    }

    private static ProductHistory history(String productId) {
        return ProductHistory.builder()
            .productId(productId)
            .name("Product-" + productId)
            .loadDate(LocalDateTime.now())
            .state("ACTIVE")
            .build();
    }

    @Test
    void findByLoadDate_queriesWithDateRange() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        ProductEntity entity = entity(1L, "prod-1", "ACTIVE");
        when(repository.findByLoadDateBetween(any(), any())).thenReturn(Flux.just(entity));

        StepVerifier.create(adapter.findByLoadDate(date))
            .assertNext(product -> {
                assertEquals("prod-1", product.getProductId());
                assertEquals("ACTIVE", product.getState());
            })
            .verifyComplete();

        verify(repository).findByLoadDateBetween(
            date.atStartOfDay(),
            date.atTime(java.time.LocalTime.MAX)
        );
    }

    @Test
    void findAll_returnsMappedProducts() {
        ProductEntity e1 = entity(1L, "p1", "ACTIVE");
        ProductEntity e2 = entity(2L, "p2", "INACTIVE");
        when(repository.findAll()).thenReturn(Flux.just(e1, e2));

        StepVerifier.create(adapter.findAll())
            .assertNext(p -> assertEquals("p1", p.getProductId()))
            .assertNext(p -> assertEquals("p2", p.getProductId()))
            .verifyComplete();
    }

    @Test
    void findAll_whenEmpty_returnsEmptyFlux() {
        when(repository.findAll()).thenReturn(Flux.empty());

        StepVerifier.create(adapter.findAll())
            .verifyComplete();
    }

    @Test
    void save_mapsAndSaves() {
        when(repository.save(any())).thenReturn(Mono.just(entity(1L, "prod-1", "ACTIVE")));

        StepVerifier.create(adapter.save(history("prod-1")))
            .expectNextCount(1)
            .verifyComplete();

        verify(repository).save(any(ProductEntity.class));
    }

    @Test
    void updateEstadoById_mutatesAndSaves() {
        ProductEntity existing = entity(1L, "prod-1", "PENDING");
        when(repository.findById(1L)).thenReturn(Mono.just(existing));
        when(repository.save(any())).thenReturn(Mono.just(existing));

        StepVerifier.create(adapter.updateEstadoById(1L, "COMPLETED"))
            .verifyComplete();

        ArgumentCaptor<ProductEntity> captor = ArgumentCaptor.forClass(ProductEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("COMPLETED", captor.getValue().getState());
    }

}
