package com.example.fileprocessor.domain.usecase;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILURE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.UNKNOWN_ERROR;

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
            HomologationRepository homologationRepository,
            String tempDirPath) {
        super(persistencePort, productRestGateway, documentValidator, tempDirPath);
        this.soapGateway = soapGateway;
        this.homologationRepository = homologationRepository;
    }

    @Override
    protected Flux<FileUploadResponse> uploadDocument(DocumentHistoryDTO history, Long docId) {
        return homologationRepository.resolve(history)
                .flatMapMany(h -> {
                    FileUploadRequest request = FileUploadRequest.from(history, docId, h);
                    return soapGateway.send(request)
                            .map(resp -> resp.toBuilder()
                                    .homologationFolder(h.homologationCountry() != null ? h.homologationCountry().homologationFolder() : null)
                                    .homologationCountry(h.homologationCountry() != null ? h.homologationCountry().homologationCountry() : null)
                                    .categoriaHomologada(h.categoriaDocument())
                                    .build());
                })
                .onErrorResume(e -> {
                    LOGGER.log(Level.SEVERE, "SOAP fatal failure for docId {0}: {1}",
                            new Object[] { docId, e.getMessage() });

                    return Flux.just(FileUploadResponse.builder()
                            .status(FAILURE.name())
                            .syncStatus(UNKNOWN_ERROR.name())
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
