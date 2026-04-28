package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.FileGateway;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.valueobject.FolderExclusionRegexConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * S3-specific document processing use case.
 */
public class S3DocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private static final Logger log = LoggerFactory.getLogger(S3DocumentProcessingUseCase.class);
    private final FolderExclusionRegexConfig folderExclusionRegex;

    public S3DocumentProcessingUseCase(
            ProductDocumentRepository documentRepository,
            ProductStatusAggregator statusAggregator,
            FileGateway fileGateway,
            FileValidator fileValidator,
            FolderExclusionRegexConfig folderExclusionRegex) {
        super(documentRepository, statusAggregator, fileGateway, fileValidator);
        this.folderExclusionRegex = folderExclusionRegex;
    }

    @Override
    protected String implementationName() {
        return "S3";
    }

    @Override
    protected Mono<ProductDocumentToProcess> prepareDocument(
            ProductDocumentToProcess pending, String traceId) {

        log.info("Preparing S3 document: {}, productId: {}",
            pending.getDocumentId(), pending.getProductId());

        // Step 1: Check folder exclusion
        if (folderExclusionRegex.shouldExclude(pending.getOrigin())) {
            log.info("S3 document {} skipped: origin matches exclusion regex: {}",
                pending.getFilename(), pending.getOrigin());
            return documentRepository.updateStatus(
                pending.getDocumentId(), DocumentStatus.SKIPPED.name(), traceId, null,
                ProcessingResultCodes.SKIPPED_FOLDER)
                .thenReturn(pending);
        }

        // Step 2: Validate document (size, extension, filename, content-type)
        return fileValidator.validate(pending);
    }
}