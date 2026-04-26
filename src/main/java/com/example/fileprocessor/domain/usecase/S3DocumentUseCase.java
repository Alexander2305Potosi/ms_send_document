package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Document processing use case that uploads documents to AWS S3.
 * Extends AbstractProcessDocumentsUseCase to leverage shared validation logic.
 *
 * CA-01, CA-02, CA-03: Product status is automatically calculated and updated
 * based on document processing results.
 */
public class S3DocumentUseCase extends AbstractProcessDocumentsUseCase {

    private static final Logger log = LoggerFactory.getLogger(S3DocumentUseCase.class);

    private final S3Gateway s3Gateway;

    public S3DocumentUseCase(ProductDocumentRepository documentRepository,
                           ProductRepository productRepository,
                           S3Gateway s3Gateway,
                           FileValidator fileValidator,
                           SoapCommunicationLogRepository logRepository,
                           FileValidationConfig validationConfig) {
        super(documentRepository, productRepository, fileValidator, logRepository, validationConfig);
        this.s3Gateway = s3Gateway;
    }

    @Override
    protected Mono<DocumentResult> sendDocument(SoapRequest request) {
        return s3Gateway.upload(request)
            .flatMap(s3Result -> {
                log.info("S3 upload successful: {} -> {}/{}",
                    request.getFilename(), s3Result.bucket(), s3Result.key());
                DocumentResult result = DocumentResult.builder()
                    .status(DocumentStatus.SUCCESS_VALUE)
                    .message(S3UseCaseConstants.MSG_UPLOAD_SUCCESS + s3Result.bucket() + "/" + s3Result.key())
                    .correlationId(s3Result.eTag())
                    .traceId(request.getTraceId())
                    .processedAt(Instant.now())
                    .externalReference(s3Result.key())
                    .success(true)
                    .build();
                // CA-07: Include documentId for audit traceability
                return saveSuccessLog(request.getDocumentId(), request.getFilename(), request.getTraceId(), result)
                    .thenReturn(result);
            })
            .onErrorResume(error -> {
                log.error("S3 upload failed for {}: {}", request.getFilename(), error.getMessage());
                return Mono.just(DocumentResult.builder()
                    .status(DocumentStatus.FAILURE_VALUE)
                    .message(S3UseCaseConstants.MSG_UPLOAD_FAILURE + error.getMessage())
                    .correlationId(null)
                    .traceId(request.getTraceId())
                    .processedAt(Instant.now())
                    .externalReference(request.getFilename())
                    .success(false)
                    .build());
            });
    }

    @Override
    protected String getImplementationName() {
        return S3UseCaseConstants.IMPL_NAME;
    }
}