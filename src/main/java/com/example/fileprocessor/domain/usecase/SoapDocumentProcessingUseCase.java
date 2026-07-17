package com.example.fileprocessor.domain.usecase;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILURE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.UNKNOWN_ERROR;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.product.ProcessingContext;
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
import java.time.LocalDateTime;
import java.util.logging.Level;

/**
 * Use case for processing documents via SOAP using generic AbstractDocumentProcessingUseCase.
 */
public class SoapDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase<Document, DocumentHistoryDTO> {

    private final DocumentPersistenceGateway persistencePort;
    private final ProductRestGateway productRestGateway;
    private final SoapGateway soapGateway;
    private final HomologationRepository homologationRepository;

    public SoapDocumentProcessingUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            RulesBussinesGateway<DocumentHistoryDTO> documentValidator,
            HomologationRepository homologationRepository,
            String tempDirPath) {
        super(persistencePort, documentValidator, tempDirPath);
        this.persistencePort = persistencePort;
        this.productRestGateway = productRestGateway;
        this.soapGateway = soapGateway;
        this.homologationRepository = homologationRepository;
    }

    @Override
    protected Flux<Document> getPendingDocuments(LocalDateTime startOfDay) {
        return persistencePort.findPendingDocumentsToday(implementationName(), startOfDay);
    }

    @Override
    protected DocumentHistoryDTO buildInitialHistory(Document doc) {
        return DocumentHistoryDTO.fromDocument(doc);
    }

    @Override
    protected Mono<ProcessingContext<DocumentHistoryDTO>> downloadDocumentContent(DocumentHistoryDTO baseHistory) {
        return productRestGateway.getDocument(baseHistory.getProductId(), baseHistory.getBusinessDocumentId())
                .map(file -> {
                    DocumentHistoryDTO updatedHistory = baseHistory.toBuilder()
                            .size(file.getSize())
                            .contentType(file.getContentType())
                            .filename(file.getFilename())
                            .originFolder(file.getOriginFolder())
                            .originCountry(file.getOriginCountry())
                            .isZip(file.getIsZip())
                            .build();
                    return new ProcessingContext<>(updatedHistory, file.getContent());
                });
    }

    @Override
    protected DocumentHistoryDTO buildDecompressedEntryHistory(DocumentHistoryDTO zipHistory, String entryName) {
        return zipHistory.toBuilder()
                .businessDocumentId(zipHistory.getBusinessDocumentId() + "/" + entryName)
                .filename(entryName)
                .contentType(com.example.fileprocessor.domain.util.MimeTypeUtil.getMimeType(entryName))
                .isZip(false)
                .build();
    }

    @Override
    protected Flux<FileUploadResponse> uploadDocument(ProcessingContext<DocumentHistoryDTO> context, Long docId) {
        DocumentHistoryDTO history = context.getHistory();
        return homologationRepository.resolve(history)
                .flatMapMany(h -> {
                    FileUploadRequest request = FileUploadRequest.from(history, context.getFileContent(), docId, h);
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
