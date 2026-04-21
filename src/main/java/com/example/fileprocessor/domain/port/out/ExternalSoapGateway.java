package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.entity.SoapResponse;
import reactor.core.publisher.Mono;

public interface ExternalSoapGateway {
    /**
     * Sends a file via SOAP protocol to an external service.
     *
     * @param request the SOAP request containing file data
     * @return Mono<SoapResponse> with the external service response
     */
    Mono<SoapResponse> sendFile(SoapRequest request);
}
