package com.example.fileprocessor.infrastructure.drivenadapters.soap;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.port.out.AnimalSoapGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Adapter implementation for Animal SOAP Gateway, delegating to the unified SoapGateway
 * to reuse SOAP XML mapper and retry configurations.
 */
@Component
@RequiredArgsConstructor
public class AnimalSoapGatewayAdapter implements AnimalSoapGateway {
    
    private final SoapGateway soapGateway;

    @Override
    public Flux<FileUploadResponse> send(FileUploadRequest request) {
        return soapGateway.send(request);
    }
}
