package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.port.out.CategoryManualRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.CategoryManualEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class CategoryManualR2dbcAdapter implements CategoryManualRepository {

    private static final Logger log = Logger.getLogger(CategoryManualR2dbcAdapter.class.getName());

    private final com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.CategoryManualRepository repository;
    private Map<String, CategoryManualEntity> cache = new ConcurrentHashMap<>();

    public CategoryManualR2dbcAdapter(
            com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.CategoryManualRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<CategoryManualEntity> findByCategoria(String categoria) {
        if (cache.isEmpty()) {
            return loadCache()
                .then(findByCategoriaFromCache(categoria));
        }
        return findByCategoriaFromCache(categoria);
    }

    private Mono<CategoryManualEntity> findByCategoriaFromCache(String categoria) {
        CategoryManualEntity entity = cache.get(categoria);
        if (entity == null) {
            entity = CategoryManualEntity.builder().categoria(categoria).build();
        }
        return Mono.just(entity);
    }

    private Mono<Void> loadCache() {
        log.log(Level.INFO, "Loading category manual cache");
        return repository.findByFechaVigenciaGreaterThanEqual(LocalDate.now())
            .collectMap(CategoryManualEntity::getCategoria)
            .doOnNext(map -> {
                cache.clear();
                cache.putAll(map);
                log.log(Level.INFO, "Category manual cache loaded with {0} entries", new Object[]{cache.size()});
            })
            .then();
    }
}