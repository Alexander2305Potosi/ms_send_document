package com.example.fileprocessor.domain.usecase;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILURE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SUCCESS;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.product.ProcessingContext;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.logging.Level;

/**
 * Use case for processing documents via S3 using generic AbstractDocumentProcessingUseCase.
 */
public class S3DocumentProcessingUseCase extends AbstractDocumentProcessingUseCase<Document, DocumentHistoryDTO> {

    private final DocumentPersistenceGateway persistencePort;
    private final ProductRestGateway productRestGateway;
    private final S3Gateway s3Gateway;

    public S3DocumentProcessingUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            S3Gateway s3Gateway,
            RulesBussinesGateway<DocumentHistoryDTO> documentValidator,
            String tempDirPath) {
        super(persistencePort, documentValidator, tempDirPath);
        this.persistencePort = persistencePort;
        this.productRestGateway = productRestGateway;
        this.s3Gateway = s3Gateway;
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
        return Mono.fromCallable(() -> FileUploadRequest.from(history, context.getFileContent(), docId, null))
            .flatMap(request -> s3Gateway.send(request))
            .flux()
            .map(response -> {
                if (response.isSuccess()) {
                    return FileUploadResponse.builder()
                        .success(true)
                        .status(SUCCESS.name())
                        .message(SUCCESS.value())
                        .processedAt(Instant.now())
                        .correlationId(response.getCorrelationId())
                        .externalReference(response.getExternalReference())
                        .build();
                }
                return response;
            })
            .onErrorResume(e -> {
                LOGGER.log(Level.SEVERE, "S3 processing failed for docId {0}: {1}", new Object[]{docId, e.getMessage()});
                return Mono.just(FileUploadResponse.builder()
                    .status(FAILURE.name())
                    .success(false)
                    .message(e.getMessage())
                    .processedAt(Instant.now())
                    .build());
            });
    }

    @Override
    protected String implementationName() {
        return "S3";
    }
}
