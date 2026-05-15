package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.DocumentHistory;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import reactor.core.publisher.Mono;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.logging.Level;

/**
 * Use case for processing documents via SOAP.
 */
public class SoapDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private final SoapGateway soapGateway;
    private final HomologationRepository homologationRepository;

    public SoapDocumentProcessingUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            RulesBussinesGateway documentValidator,
            HomologationRepository homologationRepository) {
        super(persistencePort, productRestGateway, documentValidator);
        this.soapGateway = soapGateway;
        this.homologationRepository = homologationRepository;
    }

    @Override
    protected Mono<FileUploadResponse> uploadDocument(DocumentHistory history, Long docId) {
        return homologationRepository.resolve(history.getOrigin(), history.getPais())
            .map(h -> FileUploadRequest.from(history, docId, h))
            .flatMap(request -> soapGateway.send(request))
            .onErrorResume(e -> {
                LOGGER.log(Level.SEVERE, "SOAP fatal failure for docId {0}: {1}", new Object[]{docId, e.getMessage()});
                
                return Mono.just(FileUploadResponse.builder()
                    .status(ProcessingResultCodes.FAILURE.name())
                    .errorCode(ProcessingResultCodes.UNKNOWN_ERROR.name())
                    .message(e.getMessage())
                    .stackTrace(getStackTraceAsString(e))
                    .success(false)
                    .processedAt(Instant.now())
                    .build());
            });
    }

    private String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    @Override
    protected String implementationName() {
        return "SOAP";
    }
}
