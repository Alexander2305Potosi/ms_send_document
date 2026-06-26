package com.example.fileprocessor.domain.usecase;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILURE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SUCCESS;

import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.logging.Level;

/**
 * Use case for processing documents via S3 using DocumentHistoryDTO.
 */
public class S3DocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private final S3Gateway s3Gateway;

    public S3DocumentProcessingUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            S3Gateway s3Gateway,
            RulesBussinesGateway documentValidator,
            String tempDirPath) {
        super(persistencePort, productRestGateway, documentValidator, tempDirPath);
        this.s3Gateway = s3Gateway;
    }

    @Override
    protected Flux<FileUploadResponse> uploadDocument(DocumentHistoryDTO history, Long docId) {
        return Mono.fromCallable(() -> FileUploadRequest.from(history, docId, null))
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
