package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import reactor.core.publisher.Mono;

/**
 * Document processing use case that sends documents via SOAP.
 * Extends AbstractProcessDocumentsUseCase to leverage shared validation logic.
 *
 * CA-01, CA-02, CA-03: Product status is automatically calculated and updated
 * based on document processing results.
 */
public class SoapDocumentUseCase extends AbstractProcessDocumentsUseCase {

    private final ExternalSoapGateway soapGateway;

    public SoapDocumentUseCase(ProductDocumentRepository documentRepository,
                              ProductRepository productRepository,
                              ExternalSoapGateway soapGateway,
                              FileValidator fileValidator,
                              SoapCommunicationLogRepository logRepository,
                              FileValidationConfig validationConfig) {
        super(documentRepository, productRepository, fileValidator, logRepository, validationConfig);
        this.soapGateway = soapGateway;
    }

    @Override
    protected Mono<DocumentResult> sendDocument(SoapRequest request) {
        return soapGateway.sendFile(request)
            .flatMap(response -> {
                DocumentResult result = DocumentResult.builder()
                    .status(response.getStatus())
                    .message(response.getMessage())
                    .correlationId(response.getCorrelationId())
                    .traceId(response.getTraceId())
                    .processedAt(response.getProcessedAt())
                    .externalReference(response.getExternalReference())
                    .success(response.isSuccess())
                    .build();
                // FIX #1: Encadenar el log como parte del flujo reactivo (no subscribe())
                // CA-07: Include documentId for audit traceability
                return saveSuccessLog(request.getDocumentId(), request.getFilename(), request.getTraceId(), result)
                    .thenReturn(result);
            });
    }

    @Override
    protected String getImplementationName() {
        return SoapUseCaseConstants.IMPL_NAME;
    }
}
