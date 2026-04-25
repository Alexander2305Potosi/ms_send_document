package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import reactor.core.publisher.Mono;

/**
 * Document processing use case that sends documents via SOAP.
 * Extends AbstractProcessDocumentsUseCase to leverage shared validation logic.
 */
public class SoapDocumentUseCase extends AbstractProcessDocumentsUseCase {

    private final ExternalSoapGateway soapGateway;

    public SoapDocumentUseCase(ExternalSoapGateway soapGateway) {
        super(null, null, null, null);
        this.soapGateway = soapGateway;
    }

    @Override
    protected Mono<DocumentResult> sendDocument(SoapRequest request) {
        return soapGateway.sendFile(request)
            .map(response -> DocumentResult.builder()
                .status(response.getStatus())
                .message(response.getMessage())
                .correlationId(response.getCorrelationId())
                .traceId(response.getTraceId())
                .processedAt(response.getProcessedAt())
                .externalReference(response.getExternalReference())
                .success(response.isSuccess())
                .build())
            .doOnNext(result -> saveSuccessLog(request.getFilename(), request.getTraceId(), result).subscribe());
    }

    @Override
    protected String getImplementationName() {
        return "SOAP";
    }
}
