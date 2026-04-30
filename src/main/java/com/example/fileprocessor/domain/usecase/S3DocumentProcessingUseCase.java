package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentInfo;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * S3-specific document processing use case.
 */
public class S3DocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private static final Logger log = LoggerFactory.getLogger(S3DocumentProcessingUseCase.class);

    private final S3Gateway s3Gateway;

    public S3DocumentProcessingUseCase(
            ProductRestGateway productRestGateway,
            S3Gateway s3Gateway) {
        super(productRestGateway);
        this.s3Gateway = s3Gateway;
    }

    @Override
    protected String implementationName() {
        return "S3";
    }

    @Override
    protected Mono<FileUploadResult> uploadDocument(ProductDocumentInfo doc, String productId) {
        byte[] content = doc.content() != null ? doc.content() : new byte[0];

        return s3Gateway.send(
                doc.documentId(),
                content,
                doc.filename(),
                doc.contentType(),
                content.length,
                ".",
                ".",
                doc.origin())
            .onErrorResume(error -> {
                String errorCode = error instanceof com.example.fileprocessor.domain.exception.ProcessingException pe
                    ? pe.getErrorCode() : ProcessingResultCodes.UNKNOWN_ERROR;
                return Mono.just(FileUploadResult.builder()
                    .status(DocumentStatus.FAILURE.name())
                    .errorCode(errorCode)
                    .processedAt(Instant.now())
                    .success(false)
                    .build());
            });
    }
}
