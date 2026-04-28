package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.valueobject.FolderExclusionRegexConfig;
import com.example.fileprocessor.infrastructure.helpers.config.ProcessorSettings;
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
            ProcessingDependencies deps,
            FileValidator fileValidator,
            FolderExclusionRegexConfig folderExclusionRegex,
            ProcessorSettings settings) {
        super(deps, fileValidator, new CommunicationLogFactory("S3"));
        this.folderExclusionRegex = folderExclusionRegex;
    }

    @Override
    protected String implementationName() {
        return "S3";
    }

    @Override
    protected Mono<ProductDocumentToProcess> filterByFolder(
            ProductDocumentToProcess pending, String traceId) {

        if (folderExclusionRegex.shouldExclude(pending.getOrigin())) {
            log.info("S3 document {} skipped: origin matches exclusion regex: {}",
                pending.getFilename(), pending.getOrigin());
            return documentRepository.updateStatus(
                pending.getDocumentId(), DocumentStatus.SKIPPED.name(), traceId, null,
                ProcessingResultCodes.SKIPPED_FOLDER)
                .thenReturn(pending);
        }

        return Mono.just(pending);
    }

    @Override
    protected Mono<ProductDocumentToProcess> validateDocument(
            ProductDocumentToProcess pending, String traceId) {

        log.info("Validating S3 document: {}, productId: {}",
            pending.getDocumentId(), pending.getProductId());

        return fileValidator.validate(pending);
    }
}