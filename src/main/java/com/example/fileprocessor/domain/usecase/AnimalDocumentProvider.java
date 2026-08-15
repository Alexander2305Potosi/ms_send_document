package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.animal.AnimalDocument;
import com.example.fileprocessor.domain.port.out.AnimalRepository;
import com.example.fileprocessor.domain.port.out.AnimalRestGateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class AnimalDocumentProvider {

    private final AnimalRepository animalRepository;
    private final AnimalRestGateway animalRestGateway;

    /**
     * Constructor para inicializar las dependencias del proveedor de documentos.
     * <p>
     * Secuencia:
     * 1. Asigna el repositorio de animales.
     * 2. Asigna el gateway REST de animales.
     */
    public AnimalDocumentProvider(AnimalRepository animalRepository, AnimalRestGateway animalRestGateway) {
        this.animalRepository = animalRepository;
        this.animalRestGateway = animalRestGateway;
    }

    /**
     * Obtiene todos los documentos pendientes de todos los animales desde la API externa.
     * Este método es la fuente única de verdad para descubrir documentos,
     * reutilizado tanto por el procesamiento diario como por el endpoint de control.
     * <p>
     * Secuencia:
     * 1. Consulta todos los animales en el repositorio.
     * 2. Para cada animal, llama al gateway para obtener sus documentos pendientes.
     * 3. Aplica la función distinct para filtrar documentos repetidos en base a su ID y el ID del animal.
     */
    public Flux<AnimalDocument> getAllPendingAnimalDocuments() {
        return animalRepository.findAllAnimals()
                .concatMap(animal -> animalRestGateway.getPendingDocumentsForAnimal(animal.getId()))
                .distinct(doc -> doc.getAnimalId() + "-" + doc.getDocumentId());
    }

    /**
     * Cuenta el total de documentos pendientes de todos los animales desde la API externa.
     * <p>
     * Secuencia:
     * 1. Llama a getAllPendingAnimalDocuments() para obtener el flujo de documentos.
     * 2. Ejecuta count() para retornar el número total de elementos en el flujo.
     */
    public Mono<Long> countTotalPendingDocuments() {
        return getAllPendingAnimalDocuments().count();
    }
}
