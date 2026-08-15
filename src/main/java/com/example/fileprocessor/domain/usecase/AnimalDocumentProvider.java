package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.animal.AnimalDocument;
import com.example.fileprocessor.domain.port.out.AnimalRepository;
import com.example.fileprocessor.domain.port.out.AnimalRestGateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class AnimalDocumentProvider {

    private final AnimalRepository animalRepository;
    private final AnimalRestGateway animalRestGateway;

    public AnimalDocumentProvider(AnimalRepository animalRepository, AnimalRestGateway animalRestGateway) {
        this.animalRepository = animalRepository;
        this.animalRestGateway = animalRestGateway;
    }

    /**
     * Obtiene todos los documentos pendientes de todos los animales desde la API externa.
     * Este método es la fuente única de verdad para descubrir documentos,
     * reutilizado tanto por el procesamiento diario como por el endpoint de control.
     */
    public Flux<AnimalDocument> getAllPendingAnimalDocuments() {
        return animalRepository.findAllAnimals()
                .concatMap(animal -> animalRestGateway.getPendingDocumentsForAnimal(animal.getId()))
                .distinct(doc -> doc.getAnimalId() + "-" + doc.getDocumentId());
    }

    /**
     * Cuenta el total de documentos pendientes de todos los animales desde la API externa.
     */
    public Mono<Long> countTotalPendingDocuments() {
        return getAllPendingAnimalDocuments().count();
    }
}
