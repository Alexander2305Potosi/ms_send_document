package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.HomologationResult;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.entity.CategoryManual;
import com.example.fileprocessor.domain.entity.PaisHomologado;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.CategoryManualEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.PaisHomologadoEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class HomologationR2dbcAdapter implements HomologationRepository {

    private static final Logger log = Logger.getLogger(HomologationR2dbcAdapter.class.getName());

    private final com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.CategoryManualRepository categoryRepository;
    private final com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.PaisHomologadoRepository paisRepository;

    private Map<String, CategoryManual> categoryCache = new ConcurrentHashMap<>();
    private Map<String, PaisHomologado> paisCache = new ConcurrentHashMap<>();
    private boolean cacheLoaded = false;

    public HomologationR2dbcAdapter(
            com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.CategoryManualRepository categoryRepository,
            com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.PaisHomologadoRepository paisRepository) {
        this.categoryRepository = categoryRepository;
        this.paisRepository = paisRepository;
    }

    @Override
    public Mono<HomologationResult> resolve(String origin, String pais) {
        if (!cacheLoaded) {
            return loadCache().then(resolveFromCache(origin, pais));
        }
        return resolveFromCache(origin, pais);
    }

    private Mono<HomologationResult> resolveFromCache(String origin, String pais) {
        String resolvedOrigin = origin;

        CategoryManual category = categoryCache.get(origin);
        if (category != null) {
            resolvedOrigin = category.descripcionManual() != null ? category.descripcionManual() : origin;
        }

        String resolvedPais = pais;
        PaisHomologado paisHomologado = paisCache.get(pais);
        if (paisHomologado != null) {
            resolvedPais = paisHomologado.paisHomologado() != null ? paisHomologado.paisHomologado() : pais;
        }

        return Mono.just(new HomologationResult(resolvedOrigin, resolvedPais));
    }

    private Mono<Void> loadCache() {
        log.log(Level.INFO, "Loading homologation cache");

        Mono<Void> loadCategories = categoryRepository.findAll()
            .collectMap(
                CategoryManualEntity::getCategoria,
                entity -> new CategoryManual(entity.getCategoria(), entity.getDescripcionManual())
            )
            .doOnNext(map -> {
                categoryCache.clear();
                categoryCache.putAll(map);
                log.log(Level.INFO, "Category cache loaded with {0} entries", new Object[]{categoryCache.size()});
            })
            .then();

        Mono<Void> loadPais = paisRepository.findAll()
            .collectMap(
                PaisHomologadoEntity::getPais,
                entity -> new PaisHomologado(entity.getPais(), entity.getPaisHomologado())
            )
            .doOnNext(map -> {
                paisCache.clear();
                paisCache.putAll(map);
                log.log(Level.INFO, "Pais cache loaded with {0} entries", new Object[]{paisCache.size()});
            })
            .then();

        return Mono.zip(loadCategories, loadPais)
            .doOnNext(tuple -> cacheLoaded = true)
            .then();
    }
}