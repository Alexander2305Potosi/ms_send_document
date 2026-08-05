package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.AnimalUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import reactor.core.publisher.Flux;

/**
 * Puerto SOAP especializado para el caso de uso de Animales.
 * Recibe un {@link AnimalUploadRequest} que incluye los campos específicos del canal.
 */
public interface AnimalSoapGateway {
    Flux<FileUploadResponse> send(AnimalUploadRequest request);
}
