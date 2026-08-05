package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.ProductUploadRequest;
import reactor.core.publisher.Mono;

/**
 * Puerto para el envío de documentos de Productos a AWS S3.
 * Recibe un {@link ProductUploadRequest} con los campos comunes.
 */
public interface S3Gateway {
    Mono<FileUploadResponse> send(ProductUploadRequest request);
}
