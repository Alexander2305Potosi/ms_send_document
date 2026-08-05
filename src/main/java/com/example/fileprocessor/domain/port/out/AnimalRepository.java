package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.animal.AnimalMaestro;
import reactor.core.publisher.Flux;

public interface AnimalRepository {
    Flux<AnimalMaestro> findAllAnimals();
}
