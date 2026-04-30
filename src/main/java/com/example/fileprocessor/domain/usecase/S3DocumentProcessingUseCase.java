package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.valueobject.FolderExclusionRegexConfig;
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
    private final FileValidator fileValidator;
    private final FolderExclusionRegexConfig folderExclusionRegex;

    public S3DocumentProcessingUseCase(
            ProductDocumentRepository documentRepository,
            S3Gateway s3Gateway,
            FileValidator fileValidator,
            FolderExclusionRegexConfig folderExclusionRegex,
            ProductRestGateway productRestGateway) {
        super(documentRepository, productRestGateway, new ZipProcessor(fileValidator));
        this.s3Gateway = s3Gateway;
        this.fileValidator = fileValidator;
        this.folderExclusionRegex = folderExclusionRegex;
    }

    @Override
    protected String implementationName() {
        return "S3";
    }

    @Override
    protected Mono<DocumentToUpload> applyRulesMetadata(ProductDocumentToProcess pending) {
        if (pending.isZipArchive()) {
            return processZipDocument(pending);
        }

        // Check folder exclusion first
        if (folderExclusionRegex.shouldExclude(pending.getOrigin())) {
            log.info("S3 document {} skipped: origin matches exclusion regex: {}",
                pending.getFilename(), pending.getOrigin());
            return documentRepository.updateStatus(
                    pending.getDocumentId(), DocumentStatus.SKIPPED.name(), null,
                    ProcessingResultCodes.SKIPPED_FOLDER)
                .thenReturn(new DocumentToUpload(pending, null, 0, true));
        }

        return fileValidator.validate(pending)
            .map(validDoc -> {
                FileValidator.FolderInfo folderInfo = fileValidator.extractFolderInfo(validDoc.getOrigin());
                long fileSize = validDoc.getContent() != null ? validDoc.getContent().length : 0;
                return new DocumentToUpload(validDoc, folderInfo, fileSize, false);
            });
    }

    @Override
    protected Mono<FileUploadResult> uploadDocument(DocumentToUpload doc) {
        if (doc.skipped()) {
            return Mono.just(FileUploadResult.builder()
                .status(DocumentStatus.SKIPPED.name())
                .correlationId(doc.documentId())
                .processedAt(Instant.now())
                .success(true)
                .message("Document skipped due to folder exclusion")
                .build());
        }

        FileValidator.FolderInfo folderInfo = doc.folderInfo();

        return s3Gateway.upload(
                doc.documentId(),
                doc.content(),
                doc.filename(),
                doc.contentType(),
                doc.fileSize(),
                folderInfo.parentFolder(),
                folderInfo.childFolder(),
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