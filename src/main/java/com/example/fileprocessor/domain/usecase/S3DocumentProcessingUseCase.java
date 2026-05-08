package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import reactor.core.publisher.Mono;

/**
 * Use case for processing documents via S3.
 */
public class S3DocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private final S3Gateway s3Gateway;

    public S3DocumentProcessingUseCase(
            DocumentRepository documentRepository,
            ProductRestGateway productRestGateway,
            S3Gateway s3Gateway,
            RulesBussinesGateway documentValidator) {
        super(documentRepository, productRestGateway, documentValidator);
        this.s3Gateway = s3Gateway;
    }

    @Override
    protected Mono<FileUploadResult> uploadDocument(ProductDocumentHistory doc, String productId, Long docId) {
        FileUploadRequest request = buildFileUploadRequest(doc, doc.origin(), docId);
        return s3Gateway.send(request)
            .onErrorResume(this::handleUploadError);
    }

    @Override
    protected String implementationName() {
        return "S3";
    }
}
