package com.example.fileprocessor.infrastructure.drivenadapters.soap;

import com.example.fileprocessor.domain.entity.AnimalUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.ProductUploadRequest;
import com.example.fileprocessor.domain.port.out.AnimalSoapGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Adapter del canal Animal para el envío SOAP.
 * Transforma el {@link AnimalUploadRequest} en un {@link ProductUploadRequest} genérico
 * y delega al {@link SoapGateway} unificado para reutilizar el mapper XML y la lógica de reintentos.
 */
@Component
@RequiredArgsConstructor
public class AnimalSoapGatewayAdapter implements AnimalSoapGateway {

    private final SoapGateway soapGateway;

    @Override
    public Flux<FileUploadResponse> send(AnimalUploadRequest request) {
        // Adapta el AnimalUploadRequest al contrato del SoapGateway unificado
        // preservando todos los campos comunes de la clase base.
        ProductUploadRequest productRequest = ProductUploadRequest.builder()
                .documentId(request.getDocumentId())
                .content(request.getContent())
                .filename(request.getFilename())
                .contentType(request.getContentType())
                .fileSize(request.getFileSize())
                .originFolder(request.getOriginFolder())
                .categoriaDocument(request.getCategoriaDocument())
                .homologationFolder(request.getHomologationFolder())
                .homologationCountry(request.getHomologationCountry())
                .docId(request.getDocId())
                .useCase(request.getUseCase())
                .build();

        return soapGateway.send(productRequest);
    }
}
