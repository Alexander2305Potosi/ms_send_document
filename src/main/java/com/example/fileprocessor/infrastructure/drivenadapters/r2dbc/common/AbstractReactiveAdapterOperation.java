package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.common;

import org.reactivecommons.utils.ObjectMapper;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base genérica para abstraer las operaciones repetitivas de los adaptadores reactivos.
 *
 * @param <E> Entidad de Base de Datos (Data)
 * @param <D> Entidad de Dominio (Entity)
 * @param <I> Tipo del Identificador
 * @param <R> Repositorio de Spring Data (ReactiveCrudRepository)
 */
public abstract class AbstractReactiveAdapterOperation<E, D, I, R extends ReactiveCrudRepository<E, I>> {

    protected final R repository;
    protected final ObjectMapper mapper;
    private final Function<E, D> toEntityFn;
    private final Class<E> dataClass;

    protected AbstractReactiveAdapterOperation(R repository, ObjectMapper mapper, Function<E, D> toEntityFn, Class<E> dataClass) {
        this.repository = repository;
        this.mapper = mapper;
        this.toEntityFn = toEntityFn;
        this.dataClass = dataClass;
    }

    protected E toData(D entity) {
        return mapper.map(entity, dataClass);
    }

    protected D toEntity(E data) {
        return toEntityFn.apply(data);
    }

    public Mono<D> save(D entity) {
        return repository.save(toData(entity)).map(this::toEntity);
    }



    public Flux<D> findAll() {
        return repository.findAll().map(this::toEntity);
    }

    protected Flux<D> doQueryMany(Supplier<Flux<E>> query) {
        return query.get().map(this::toEntity);
    }

}
