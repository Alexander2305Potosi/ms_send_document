package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.product.Document;
import reactor.core.publisher.Flux;

/**
 * Puerto de salida para consultar documentos pendientes de animales.
 * La complejidad de obtener directorios, aplanar el árbol y filtrar
 * por Source queda encapsulada en el Adapter de infraestructura.
 */
public interface AnimalRestGateway {
    Flux<Document> getPendingDocumentsForAnimal(Long animalId);
}
