package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentStatus;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * SOAP-specific document processing use case.
 */
public class SoapDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private static final Logger log = LoggerFactory.getLogger(SoapDocumentProcessingUseCase.class);

    private final SoapGateway soapGateway;
    private final FileValidator fileValidator;

    public SoapDocumentProcessingUseCase(
            ProductDocumentRepository documentRepository,
            SoapGateway soapGateway,
            FileValidator fileValidator,
            ProductRestGateway productRestGateway) {
        super(documentRepository, productRestGateway, new ZipProcessor(fileValidator));
        this.soapGateway = soapGateway;
        this.fileValidator = fileValidator;
    }

    @Override
    protected String implementationName() {
        return "SOAP";
    }

    @Override
    protected Mono<DocumentToUpload> applyRulesMetadata(ProductDocumentToProcess pending) {
        if (pending.isZipArchive()) {
            return processZipDocument(pending);
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
                .message("Document skipped")
                .build());
        }

        FileValidator.FolderInfo folderInfo = doc.folderInfo();

        return soapGateway.send(
                doc.documentId(),
                doc.content(),
                doc.filename(),
                doc.contentType(),
                doc.fileSize(),
                folderInfo.parentFolder(),
                folderInfo.childFolder())
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