package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentR2dbcAdapterTest {

    @Mock
    private DocumentRepository springDataRepository;

    @Mock
    private DatabaseClient databaseClient;

    @Mock
    private DatabaseClient.GenericExecuteSpec executeSpec;

    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;

    private DocumentR2dbcAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DocumentR2dbcAdapter(springDataRepository, databaseClient);
    }

    @Test
    void save_delegatesToSpringDataRepository() {
        Document domain = Document.builder().id(1L).name("test.pdf").build();
        DocumentEntity entity = DocumentEntity.builder().id(1L).name("test.pdf").build();

        when(springDataRepository.save(any())).thenReturn(Mono.just(entity));

        StepVerifier.create(adapter.save(domain))
            .assertNext(res -> {
                assertEquals(1L, res.id());
                assertEquals("test.pdf", res.name());
            })
            .verifyComplete();
    }

    @Test
    void updateStateAndRetry_executesSqlCorrectly() {
        when(databaseClient.sql(anyString())).thenReturn(executeSpec);
        when(executeSpec.bind(anyString(), any())).thenReturn(executeSpec);
        when(executeSpec.fetch()).thenReturn(fetchSpec);
        
        when(fetchSpec.rowsUpdated()).thenReturn(Mono.just(1L));

        StepVerifier.create(adapter.updateStateAndRetry(1L, "PENDING", "IN_PROGRESS", 0, LocalDateTime.now()))
            .expectNext(1L)
            .verifyComplete();

        verify(databaseClient).sql(contains("UPDATE documentos SET estado = :newState"));
    }
}
