package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.homologation.HomologationResult;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.CategoryManualEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.PaisHomologadoEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.CategoryManualRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.PaisHomologadoRepository;
import com.example.fileprocessor.infrastructure.helpers.rule.JsonRuleEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HomologationR2dbcAdapterTest {

    private CategoryManualRepository categoryRepository;
    private PaisHomologadoRepository paisRepository;
    private JsonRuleEvaluator ruleEvaluator;
    private HomologationR2dbcAdapter adapter;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryManualRepository.class);
        paisRepository = mock(PaisHomologadoRepository.class);
        ruleEvaluator = mock(JsonRuleEvaluator.class);
        adapter = new HomologationR2dbcAdapter(categoryRepository, paisRepository, ruleEvaluator);
    }

    @Test
    void testResolveLoadsCacheAndResolves() {
        CategoryManualEntity catEntity = new CategoryManualEntity(1L, "DOC-OK", "CategoryOk");
        PaisHomologadoEntity paisEntity = new PaisHomologadoEntity(1L, 1, "{\"field\":\"value\"}", "homologated_folder", "MX");

        when(categoryRepository.findAll()).thenReturn(Flux.just(catEntity));
        when(paisRepository.findAll()).thenReturn(Flux.just(paisEntity));
        
        // Mock evaluator to match the rule
        when(ruleEvaluator.evaluate(any(), any())).thenReturn(true);

        DocumentHistoryDTO history = DocumentHistoryDTO.builder()
                .businessDocumentId("DOC-OK-01")
                .originFolder("origin_folder")
                .originCountry("US")
                .build();

        StepVerifier.create(adapter.resolve(history))
                .assertNext(result -> {
                    assertEquals("CategoryOk", result.categoriaDocument());
                    assertEquals("homologated_folder", result.homologationCountry().homologationFolder());
                    assertEquals("MX", result.homologationCountry().homologationCountry());
                })
                .expectComplete()
                .verify();

        // Second call should resolve directly from cache
        StepVerifier.create(adapter.resolve(history))
                .assertNext(result -> {
                    assertEquals("CategoryOk", result.categoriaDocument());
                })
                .expectComplete()
                .verify();

        // Repository should be called only once
        verify(categoryRepository, times(1)).findAll();
        verify(paisRepository, times(1)).findAll();
    }

    @Test
    void testResolveWhenNoPrefixMatchesCategoryDefaultsToDocId() {
        CategoryManualEntity catEntity = new CategoryManualEntity(1L, "DOC-OTHER", "OtherCategory");
        PaisHomologadoEntity paisEntity = new PaisHomologadoEntity(1L, 1, null, "homologated_folder", "MX");

        when(categoryRepository.findAll()).thenReturn(Flux.just(catEntity));
        when(paisRepository.findAll()).thenReturn(Flux.just(paisEntity));
        when(ruleEvaluator.evaluate(any(), any())).thenReturn(false);

        DocumentHistoryDTO history = DocumentHistoryDTO.builder()
                .businessDocumentId("DOC-OK-01")
                .originFolder("origin_folder")
                .originCountry("US")
                .build();

        StepVerifier.create(adapter.resolve(history))
                .assertNext(result -> {
                    assertEquals("DOC-OK-01", result.categoriaDocument());
                    assertEquals("origin_folder", result.homologationCountry().homologationFolder());
                    assertEquals("US", result.homologationCountry().homologationCountry());
                })
                .expectComplete()
                .verify();
    }
}
