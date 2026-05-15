package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.DocumentHistory;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import reactor.core.publisher.Mono;

import java.io.PrintWriter;
import java.io.StringWriter;
import com.example.fileprocessor.domain.util.ExceptionUtils;

import java.time.Instant;
import java.util.logging.Level;

/**
 * Use case for processing documents via S3.
 */
public class S3DocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private final S3Gateway s3Gateway;

    public S3DocumentProcessingUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            S3Gateway s3Gateway,
            RulesBussinesGateway documentValidator) {
        super(persistencePort, productRestGateway, documentValidator);
        this.s3Gateway = s3Gateway;
    }

    /**
     * Uploads the document to the AWS S3 destination.
     *
     * @param history the document metadata and content.
     * @param docId the database ID of the document.
     * @return a Mono of FileUploadResponse containing the S3 upload result.
     */
    @Override
    protected Mono<FileUploadResponse> uploadDocument(DocumentHistory history, Long docId) {
        return Mono.fromCallable(() -> FileUploadRequest.from(history, docId, null))
            .flatMap(request -> s3Gateway.send(request))
            .map(response -> {
                if (response.isSuccess()) {
                    return FileUploadResponse.builder()
                        .success(true)
                        .status(ProcessingResultCodes.SUCCESS.name())
                        .message("Documento procesado correctamente en S3")
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
                    .status(ProcessingResultCodes.FAILURE.name())
                    .success(false)
                    .message(e.getMessage())
                    .stackTrace(ExceptionUtils.getStackTraceAsString(e))
                    .processedAt(Instant.now())
                    .build());
            });
    }

    /**
     * Returns the name of this implementation.
     *
     * @return "S3"
     */
    @Override
    protected String implementationName() {
        return "S3";
    }
}
