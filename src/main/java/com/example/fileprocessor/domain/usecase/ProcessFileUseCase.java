package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.DocumentInfo;
import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.domain.entity.FileUploadResult;
import com.example.fileprocessor.domain.entity.SoapCommunicationLog;
import com.example.fileprocessor.domain.entity.SoapRequest;
import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.domain.entity.ZipArchive;
import com.example.fileprocessor.domain.port.out.DocumentRestGateway;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import com.example.fileprocessor.infrastructure.soap.exception.SoapCommunicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProcessFileUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessFileUseCase.class);

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILURE = "FAILURE";
    private static final String DEFAULT_ERROR_CODE = "UNKNOWN_ERROR";
    private static final int DEFAULT_RETRY_COUNT = 0;

    private final DocumentRestGateway documentGateway;
    private final ExternalSoapGateway soapGateway;
    private final FileValidator fileValidator;
    private final SoapCommunicationLogRepository logRepository;

    public ProcessFileUseCase(DocumentRestGateway documentGateway,
                              ExternalSoapGateway soapGateway,
                              FileValidator fileValidator,
                              SoapCommunicationLogRepository logRepository) {
        this.documentGateway = documentGateway;
        this.soapGateway = soapGateway;
        this.fileValidator = fileValidator;
        this.logRepository = logRepository;
    }

    public Mono<FileUploadResult> execute(String documentId, String traceId) {
        log.info("Processing document: {}, traceId: {}", documentId, traceId);

        return documentGateway.getDocument(documentId, traceId)
            .flatMap(this::processDocument)
            .doOnNext(response -> log.info("Document {} processed successfully, correlationId: {}",
                documentId, response.getCorrelationId()))
            .doOnError(error -> log.error("Error processing document {}: {}",
                documentId, error.getMessage()));
    }

    public Flux<FileUploadResult> executeAll(String traceId) {
        log.info("Processing all documents, traceId: {}", traceId);

        return documentGateway.getAllDocuments(traceId)
            .flatMap(this::processDocument)
            .doOnNext(response -> log.info("Document processed successfully, correlationId: {}",
                response.getCorrelationId()))
            .doOnError(error -> log.error("Error processing document: {}", error.getMessage()));
    }

    private Mono<FileUploadResult> processDocument(DocumentInfo doc) {
        if (doc.isZipArchive()) {
            log.info("Document {} is a ZIP archive, extracting and processing contents", doc.getDocumentId());
            return processZipDocument(doc);
        }
        return processFile(toFileData(doc, null));
    }

    private FileData toFileData(DocumentInfo doc, String parentDocumentId) {
        return FileData.builder()
            .content(doc.getContent())
            .filename(doc.getFilename())
            .size(doc.getSize())
            .contentType(doc.getContentType())
            .traceId(java.util.UUID.randomUUID().toString())
            .parentDocumentId(parentDocumentId)
            .build();
    }

    private Mono<FileUploadResult> processZipDocument(DocumentInfo doc) {
        String zipTraceId = java.util.UUID.randomUUID().toString();

        return logRepository.findByParentDocumentId(doc.getDocumentId())
            .collectList()
            .flatMap(processedLogs -> {
                Set<String> processedFiles = extractProcessedFilenames(processedLogs);
                ZipArchive zipArchive = ZipArchive.builder()
                    .zipContent(doc.getContent())
                    .originalFilename(doc.getFilename())
                    .build();

                List<ZipArchive.ExtractedDocument> extractedDocs;
                try {
                    extractedDocs = zipArchive.extractDocuments();
                } catch (IOException e) {
                    log.error("Failed to extract ZIP archive {}: {}", doc.getFilename(), e.getMessage());
                    return logFailure(doc.getDocumentId(), zipTraceId, e, doc.getDocumentId())
                        .then(Mono.error(new IllegalStateException("Failed to extract ZIP: " + doc.getFilename(), e)));
                }

                if (extractedDocs.isEmpty()) {
                    log.warn("ZIP archive {} is empty", doc.getFilename());
                    return Mono.error(new IllegalStateException("ZIP archive is empty: " + doc.getFilename()));
                }

                List<ZipArchive.ExtractedDocument> docsToProcess = extractedDocs.stream()
                    .filter(extracted -> !processedFiles.contains(extracted.getFilename()))
                    .toList();

                if (docsToProcess.isEmpty()) {
                    log.info("All documents in ZIP {} already processed, skipping", doc.getFilename());
                    return buildResumeResult(processedLogs, doc.getDocumentId(), zipTraceId);
                }

                if (docsToProcess.size() < extractedDocs.size()) {
                    log.info("Resuming ZIP processing: {}/{} documents already processed, {} remaining",
                        processedFiles.size(), extractedDocs.size(), docsToProcess.size());
                } else {
                    log.info("ZIP archive {} contains {} documents", doc.getFilename(), extractedDocs.size());
                }

                return Flux.fromIterable(docsToProcess)
                    .map(extracted -> toFileData(extracted, doc.getDocumentId()))
                    .flatMap(this::processFile)
                    .collectList()
                    .map(results -> aggregateResults(results, doc.getDocumentId(), zipTraceId));
            });
    }

    private FileData toFileData(ZipArchive.ExtractedDocument extracted, String parentDocumentId) {
        return FileData.builder()
            .content(extracted.getContent())
            .filename(extracted.getFilename())
            .size(extracted.getSize())
            .contentType(extracted.getContentType())
            .traceId(java.util.UUID.randomUUID().toString())
            .parentDocumentId(parentDocumentId)
            .build();
    }

    private Mono<FileUploadResult> processFile(FileData fileData) {
        return fileValidator.validate(fileData)
            .map(SoapRequest::fromFileData)
            .flatMap(soapGateway::sendFile)
            .flatMap(response -> logSuccess(fileData.getFilename(), fileData.getTraceId(), response, fileData.getParentDocumentId())
                .thenReturn(response))
            .map(this::toResult)
            .doOnNext(result -> log.info("Document {} processed, correlationId: {}", fileData.getFilename(), result.getCorrelationId()))
            .onErrorResume(throwable -> hasSoapCommunicationException(throwable)
                ? logFailure(fileData.getFilename(), fileData.getTraceId(), throwable, fileData.getParentDocumentId())
                    .then(Mono.error(throwable))
                : Mono.error(throwable));
    }

    private Set<String> extractProcessedFilenames(List<SoapCommunicationLog> logs) {
        Set<String> processed = new HashSet<>();
        for (SoapCommunicationLog logEntry : logs) {
            if (STATUS_SUCCESS.equals(logEntry.getStatus())) {
                processed.add(logEntry.getFilename());
            }
        }
        return processed;
    }

    private Mono<FileUploadResult> buildResumeResult(List<SoapCommunicationLog> logs, String documentId, String zipTraceId) {
        long successCount = logs.stream().filter(l -> STATUS_SUCCESS.equals(l.getStatus())).count();
        long totalProcessed = logs.size();

        SoapCommunicationLog firstSuccess = logs.stream()
            .filter(l -> STATUS_SUCCESS.equals(l.getStatus()))
            .findFirst()
            .orElse(null);

        return Mono.just(FileUploadResult.builder()
            .status(STATUS_SUCCESS)
            .message(String.format("ZIP resumed: %d/%d documents previously successful", successCount, totalProcessed))
            .correlationId(firstSuccess != null ? firstSuccess.getFilename() + "-resume" : null)
            .traceId(zipTraceId)
            .processedAt(Instant.now())
            .externalReference(documentId + " (ZIP-RESUMED)")
            .success(true)
            .build());
    }

    private FileUploadResult aggregateResults(List<FileUploadResult> results, String documentId, String zipTraceId) {
        if (results.isEmpty()) {
            return FileUploadResult.builder()
                .status(STATUS_FAILURE)
                .message("No documents processed")
                .correlationId(null)
                .traceId(zipTraceId)
                .processedAt(Instant.now())
                .externalReference(documentId + " (ZIP)")
                .success(false)
                .build();
        }

        var stats = results.stream().collect(
            java.util.stream.Collectors.partitioningBy(FileUploadResult::isSuccess)
        );

        List<FileUploadResult> successes = stats.get(true);
        List<FileUploadResult> failures = stats.get(false);

        FileUploadResult referenceResult = successes.isEmpty() ? failures.get(failures.size() - 1) : successes.get(0);
        boolean allSuccess = failures.isEmpty();

        return FileUploadResult.builder()
            .status(referenceResult.getStatus())
            .message(String.format("ZIP processed: %d documents, %d successful%s",
                results.size(), successes.size(), allSuccess ? "" : ", some failed"))
            .correlationId(referenceResult.getCorrelationId())
            .traceId(zipTraceId)
            .processedAt(Instant.now())
            .externalReference(documentId + " (ZIP)")
            .success(allSuccess)
            .build();
    }

    private FileUploadResult toResult(SoapResponse response) {
        return FileUploadResult.builder()
            .status(response.getStatus())
            .message(response.getMessage())
            .correlationId(response.getCorrelationId())
            .traceId(response.getTraceId())
            .processedAt(response.getProcessedAt())
            .externalReference(response.getExternalReference())
            .success(response.isSuccess())
            .build();
    }

    private boolean hasSoapCommunicationException(Throwable throwable) {
        return throwable instanceof SoapCommunicationException
            || throwable.getCause() instanceof SoapCommunicationException;
    }

    private Mono<Void> logSuccess(String filename, String traceId, SoapResponse response, String parentDocumentId) {
        return saveLog(filename, traceId, STATUS_SUCCESS, null, DEFAULT_RETRY_COUNT, parentDocumentId);
    }

    private Mono<Void> logFailure(String filename, String traceId, Throwable error, String parentDocumentId) {
        SoapCommunicationException sce = unwrapSoapException(error);
        String errorCode = sce != null ? sce.getErrorCode() : DEFAULT_ERROR_CODE;
        int retries = sce != null ? sce.getRetryCount() : DEFAULT_RETRY_COUNT;
        return saveLog(filename, traceId, STATUS_FAILURE, errorCode, retries, parentDocumentId);
    }

    private Mono<Void> saveLog(String filename, String traceId, String status,
                               String errorCode, int retries, String parentDocumentId) {
        SoapCommunicationLog dbLog = SoapCommunicationLog.builder()
            .traceId(traceId)
            .status(status)
            .retryCount(retries)
            .errorCode(errorCode)
            .filename(filename)
            .parentDocumentId(parentDocumentId)
            .createdAt(Instant.now())
            .build();
        return logRepository.save(dbLog).then();
    }

    private SoapCommunicationException unwrapSoapException(Throwable error) {
        if (error instanceof SoapCommunicationException sce) {
            return sce;
        }
        if (error.getCause() instanceof SoapCommunicationException sce) {
            return sce;
        }
        return null;
    }
}
