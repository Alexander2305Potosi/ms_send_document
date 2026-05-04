package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.port.out.PaisHomologadoRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.PaisHomologadoEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class PaisHomologadoR2dbcAdapter implements PaisHomologadoRepository {

    private static final Logger log = Logger.getLogger(PaisHomologadoR2dbcAdapter.class.getName());

    private final com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.PaisHomologadoRepository repository;
    private Map<String, PaisHomologadoEntity> cache = new ConcurrentHashMap<>();

    public PaisHomologadoR2dbcAdapter(
            com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.PaisHomologadoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<PaisHomologadoEntity> findByPais(String pais) {
        if (cache.isEmpty()) {
            return loadCache()
                .then(findByPaisFromCache(pais));
        }
        return findByPaisFromCache(pais);
    }

    private Mono<PaisHomologadoEntity> findByPaisFromCache(String pais) {
        PaisHomologadoEntity entity = cache.get(pais);
        if (entity == null) {
            entity = PaisHomologadoEntity.builder().pais(pais).build();
        }
        return Mono.just(entity);
    }

    private Mono<Void> loadCache() {
        log.log(Level.INFO, "Loading pais homologado cache");
        return repository.findAll()
            .collectMap(PaisHomologadoEntity::getPais)
            .doOnNext(map -> {
                cache.clear();
                cache.putAll(map);
                log.log(Level.INFO, "Pais homologado cache loaded with {0} entries", new Object[]{cache.size()});
            })
            .then();
    }
}