package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.animal.AnimalMaestro;
import com.example.fileprocessor.domain.port.out.AnimalRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.AnimalMaestroRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnimalR2dbcAdapter implements AnimalRepository {

    private final AnimalMaestroRepository maestroRepository;

    @Override
    public Flux<AnimalMaestro> findAllAnimals() {
        return maestroRepository.findAll()
                .map(entity -> AnimalMaestro.builder()
                        .id(entity.getId())
                        .name(entity.getName())
                        .category(entity.getCategory())
                        .build());
    }
}
