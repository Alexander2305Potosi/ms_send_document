package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.homologation.HomologationResult;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.entity.homologation.CategoryManual;
import com.example.fileprocessor.domain.entity.homologation.PaisHomologado;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.CategoryManualEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.PaisHomologadoEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.CategoryManualRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.PaisHomologadoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
@RequiredArgsConstructor
public class HomologationR2dbcAdapter implements HomologationRepository {

    private static final Logger log = Logger.getLogger(HomologationR2dbcAdapter.class.getName());

    private final CategoryManualRepository categoryRepository;
    private final PaisHomologadoRepository paisRepository;

    private final Map<String, CategoryManual> categoryCache = new ConcurrentHashMap<>();
    private final Map<String, PaisHomologado> paisCache = new ConcurrentHashMap<>();
    private boolean cacheLoaded = false;

    @Override
    public Mono<HomologationResult> resolve(String origin, String pais) {
        if (!cacheLoaded) {
            return loadCache().then(Mono.defer(() -> resolveFromCache(origin, pais)));
        }
        return resolveFromCache(origin, pais);
    }

    private Mono<HomologationResult> resolveFromCache(String origin, String pais) {
        String resolvedOrigin = origin;
        if (origin != null) {
            CategoryManual category = categoryCache.get(origin);
            if (category != null) {
                resolvedOrigin = category.descripcionManual() != null ? category.descripcionManual() : origin;
            }
        }

        String resolvedPais = pais;
        if (pais != null) {
            PaisHomologado p = paisCache.get(pais);
            if (p != null) {
                resolvedPais = p.paisHomologado() != null ? p.paisHomologado() : pais;
            }
        }

        return Mono.just(new HomologationResult(resolvedOrigin, resolvedPais));
    }

    private Mono<Void> loadCache() {
        log.log(Level.INFO, "Loading homologation cache from database");

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
            .doOnNext(tuple -> {
                cacheLoaded = true;
                log.log(Level.INFO, "Full homologation cache initialized successfully");
            })
            .then();
    }
}
