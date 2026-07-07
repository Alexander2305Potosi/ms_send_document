package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.animal.AnimalMaestro;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.AnimalMaestroEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.AnimalMaestroRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnimalR2dbcAdapterTest {

    @Mock
    private AnimalMaestroRepository maestroRepository;

    @InjectMocks
    private AnimalR2dbcAdapter adapter;

    @Test
    void findAllAnimalsMapsEntityToDomain() {
        AnimalMaestroEntity entity = AnimalMaestroEntity.builder()
                .id(1L)
                .name("Cow")
                .category("Mammal")
                .build();

        when(maestroRepository.findAll()).thenReturn(Flux.just(entity));

        StepVerifier.create(adapter.findAllAnimals())
                .expectNextMatches(animal -> 
                        animal.getId().equals(1L) && 
                        "Cow".equals(animal.getName()) && 
                        "Mammal".equals(animal.getCategory()))
                .expectComplete()
                .verify();
    }
}
