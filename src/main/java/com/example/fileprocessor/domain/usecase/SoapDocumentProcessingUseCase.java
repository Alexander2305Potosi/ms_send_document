package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.logging.Level;

/**
 * Use case for processing documents via SOAP using DocumentHistoryDTO.
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
    protected Flux<FileUploadResponse> uploadDocument(DocumentHistoryDTO history, Long docId) {
        return homologationRepository.resolve(history.getOrigin(), history.getPais())
                .map(h -> FileUploadRequest.from(history, docId, h))
                .flatMapMany(request -> soapGateway.send(request))
                .onErrorResume(e -> {
                    LOGGER.log(Level.SEVERE, "SOAP fatal failure for docId {0}: {1}",
                            new Object[] { docId, e.getMessage() });

                    return Flux.just(FileUploadResponse.builder()
                            .status(ProcessingResultCodes.FAILURE.name())
                            .errorCode(ProcessingResultCodes.UNKNOWN_ERROR.name())
                            .message(e.getMessage())
                            .success(false)
                            .filename(history.getFilename())
                            .processedAt(Instant.now())
                            .build());
                });
    }

    @Override
    protected String implementationName() {
        return "SOAP";
    }
}
