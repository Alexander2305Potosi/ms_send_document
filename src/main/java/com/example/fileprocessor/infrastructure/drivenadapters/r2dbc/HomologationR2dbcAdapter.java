package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.homologation.HomologationCountry;
import com.example.fileprocessor.domain.entity.homologation.HomologationResult;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.entity.homologation.CategoryManual;
import com.example.fileprocessor.domain.entity.homologation.PaisHomologado;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.PaisHomologadoEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.CategoryManualRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.PaisHomologadoRepository;
import com.example.fileprocessor.infrastructure.helpers.rule.JsonRuleEvaluator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
@RequiredArgsConstructor
public class HomologationR2dbcAdapter implements HomologationRepository {

    private static final Logger log = Logger.getLogger(HomologationR2dbcAdapter.class.getName());

    private final CategoryManualRepository categoryRepository;
    private final PaisHomologadoRepository paisRepository;
    private final JsonRuleEvaluator ruleEvaluator;
    private final ObjectMapper mapper = new ObjectMapper();

    private record PaisCacheEntry(PaisHomologado pais, JsonNode ruleNode) {}
    
    private final List<CategoryManual> categoryCache = new CopyOnWriteArrayList<>();
    private final List<PaisCacheEntry> paisCache = new CopyOnWriteArrayList<>();
    private boolean cacheLoaded = false;

    @Override
    public Mono<HomologationResult> resolve(DocumentHistoryDTO history) {
        if (!cacheLoaded) {
            return loadCache().then(Mono.defer(() -> resolveFromCache(history)));
        }
        return resolveFromCache(history);
    }

    private Mono<HomologationResult> resolveFromCache(DocumentHistoryDTO history) {
        String documentId = history.getBusinessDocumentId() != null ? history.getBusinessDocumentId() : "";

        // 1. Resolve Category by prefix
        String categoriaDocument = documentId; // default to documentId or empty
        for (CategoryManual category : categoryCache) {
            if (documentId.startsWith(category.prefijo())) {
                categoriaDocument = category.categoriaHomologado();
                break;
            }
        }

        // 2. Resolve Country and Folder by dynamic JSON engine
        JsonNode dtoNode = mapper.valueToTree(history);
        String homologationFolder = history.getOriginFolder();
        String homologationCountry = history.getOriginCountry();

        for (PaisCacheEntry entry : paisCache) {
            if (ruleEvaluator.evaluate(entry.ruleNode(), dtoNode)) {
                homologationFolder = entry.pais().homologationFolder();
                homologationCountry = entry.pais().homologationCountry();
                break;
            }
        }

        HomologationCountry hc = HomologationCountry.builder()
            .homologationFolder(homologationFolder)
            .homologationCountry(homologationCountry)
            .build();

        return Mono.just(HomologationResult.builder()
            .categoriaDocument(categoriaDocument)
            .homologationCountry(hc)
            .build());
    }

    private Mono<Void> loadCache() {
        log.log(Level.INFO, "Loading homologation cache from database");

        Mono<Void> loadCategories = categoryRepository.findAll()
            .map(entity -> new CategoryManual(entity.getPrefijo(), entity.getCategoriaHomologado()))
            .collectList()
            .doOnNext(list -> {
                categoryCache.clear();
                categoryCache.addAll(list);
                log.log(Level.INFO, "Category cache loaded with {0} entries", new Object[]{categoryCache.size()});
            })
            .then();

        Mono<Void> loadPais = paisRepository.findAll()
            .collectSortedList(java.util.Comparator.comparing(PaisHomologadoEntity::getOrden))
            .map(list -> list.stream()
                .map(entity -> {
                    JsonNode ruleNode;
                    try {
                        ruleNode = mapper.readTree(entity.getCondicionJsonb());
                    } catch (Exception e) {
                        ruleNode = mapper.createObjectNode();
                    }
                    PaisHomologado domain = new PaisHomologado(
                        entity.getOrden(),
                        entity.getCondicionJsonb() != null ? entity.getCondicionJsonb() : "{}",
                        entity.getHomologationFolder(),
                        entity.getHomologationCountry()
                    );
                    return new PaisCacheEntry(domain, ruleNode);
                }).toList()
            )
            .doOnNext(list -> {
                paisCache.clear();
                paisCache.addAll(list);
                log.log(Level.INFO, "Pais cache loaded with {0} entries", new Object[]{paisCache.size()});
            })
            .then();

        return Mono.zip(loadCategories, loadPais)
            .doOnNext(tuple -> {
                cacheLoaded = true;
                log.log(Level.INFO, "Full homologation cache initialized successfully");
            })
            .then();
    }
}
