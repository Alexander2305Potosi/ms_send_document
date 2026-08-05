package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.ProductUploadRequest;
import reactor.core.publisher.Flux;

/**
 * Puerto para el envío de documentos de Productos vía protocolo SOAP.
 * Recibe un {@link ProductUploadRequest} con los campos comunes y de homologación.
 */
public interface SoapGateway {
    Flux<FileUploadResponse> send(ProductUploadRequest request);
}
