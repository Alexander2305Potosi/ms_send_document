package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.common;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base genérica para abstraer las operaciones repetitivas de los adaptadores reactivos.
 * Basado en patrones comunes de Clean Architecture encontrados en internet.
 *
 * @param <E> Entidad de Base de Datos
 * @param <D> Entidad de Dominio
 * @param <I> Tipo del Identificador
 * @param <R> Repositorio de Spring Data (ReactiveCrudRepository)
 */
public abstract class AbstractReactiveAdapterOperation<E, D, I, R extends ReactiveCrudRepository<E, I>> {

    protected final R repository;
    private final Function<D, E> toEntity;
    private final Function<E, D> toDomain;

    protected AbstractReactiveAdapterOperation(R repository, Function<D, E> toEntity, Function<E, D> toDomain) {
        this.repository = repository;
        this.toEntity = toEntity;
        this.toDomain = toDomain;
    }

    public Mono<D> save(D domain) {
        return repository.save(toEntity.apply(domain)).map(toDomain);
    }

    public Flux<D> saveAll(Flux<D> domains) {
        return repository.saveAll(domains.map(toEntity)).map(toDomain);
    }

    public Mono<D> findById(I id) {
        return repository.findById(id).map(toDomain);
    }

    public Flux<D> findAll() {
        return repository.findAll().map(toDomain);
    }

    protected Flux<D> doQueryMany(Supplier<Flux<E>> query) {
        return query.get().map(toDomain);
    }

    protected Mono<D> doQuery(Supplier<Mono<E>> query) {
        return query.get().map(toDomain);
    }
}
