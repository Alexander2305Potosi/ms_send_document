package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocument;
import com.example.fileprocessor.domain.port.out.DocumentTraceabilityGateway;
import com.example.fileprocessor.domain.port.out.ProductDbGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import reactor.core.publisher.Mono;

/**
 * S3-specific document processing use case.
 */
public class S3DocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private final S3Gateway s3Gateway;

    public S3DocumentProcessingUseCase(
            ProductDbGateway productDbGateway,
            ProductRestGateway productRestGateway,
            S3Gateway s3Gateway,
            DocumentTraceabilityGateway traceabilityGateway,
            RulesBussinesGateway documentValidator) {
        super(productDbGateway, productRestGateway, documentValidator, traceabilityGateway);
        this.s3Gateway = s3Gateway;
    }

    @Override
    protected Mono<FileUploadResult> uploadDocument(ProductDocument doc, String productId) {
        FileUploadRequest request = buildFileUploadRequest(doc, doc.origin());
        return s3Gateway.send(request)
            .onErrorResume(this::handleUploadError);
    }

    @Override
    protected String implementationName() {
        return "S3";
    }
}
