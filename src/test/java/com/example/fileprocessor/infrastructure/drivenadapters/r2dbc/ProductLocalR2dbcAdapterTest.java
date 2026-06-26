package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.function.BiFunction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductLocalR2dbcAdapterTest {

    @Mock
    private DatabaseClient databaseClient;

    @Mock
    private GenericExecuteSpec executeSpec;

    @Mock
    private RowsFetchSpec<String> fetchSpec;

    private ProductLocalR2dbcAdapter adapter;
    private final String query = "SELECT sucursal FROM schema.table WHERE id_producto = :productId";

    @BeforeEach
    void setUp() {
        adapter = new ProductLocalR2dbcAdapter(databaseClient, query);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findBranchByProductIdReturnsBranchName() {
        when(databaseClient.sql(query)).thenReturn(executeSpec);
        when(executeSpec.bind(eq("productId"), eq("p1"))).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(fetchSpec);
        when(fetchSpec.one()).thenReturn(Mono.just("Sucursal Bogota"));

        StepVerifier.create(adapter.findBranchByProductId("p1"))
                .expectNext("Sucursal Bogota")
                .verifyComplete();

        verify(databaseClient).sql(query);
        verify(executeSpec).bind("productId", "p1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findBranchByProductIdWhenErrorReturnsEmpty() {
        when(databaseClient.sql(query)).thenReturn(executeSpec);
        when(executeSpec.bind(eq("productId"), eq("p2"))).thenReturn(executeSpec);
        when(executeSpec.map(any(BiFunction.class))).thenReturn(fetchSpec);
        when(fetchSpec.one()).thenReturn(Mono.error(new RuntimeException("DB error")));

        StepVerifier.create(adapter.findBranchByProductId("p2"))
                .verifyComplete(); // returns Mono.empty() on error

        verify(databaseClient).sql(query);
        verify(executeSpec).bind("productId", "p2");
    }
}
