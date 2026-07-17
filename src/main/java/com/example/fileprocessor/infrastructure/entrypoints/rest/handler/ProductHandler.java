package com.example.fileprocessor.infrastructure.entrypoints.rest.handler;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.usecase.AbstractDocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.S3DocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.GetStatusUseCase;
import com.example.fileprocessor.domain.usecase.SyncDocumentsUseCase;
import com.example.fileprocessor.domain.usecase.AnimalDocumentProcessingUseCase;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants.*;

@Component
public class ProductHandler {

    private static final Logger LOGGER = Logger.getLogger(ProductHandler.class.getName());

    private final AbstractDocumentProcessingUseCase<Document, DocumentHistoryDTO> soapDocumentUseCase;
    private final ObjectProvider<S3DocumentProcessingUseCase> s3DocumentUseCaseProvider;
    private final SyncDocumentsUseCase syncDocumentsUseCase;
    private final GetStatusUseCase getStatusUseCase;
    private final AnimalDocumentProcessingUseCase animalDocumentProcessingUseCase;

    public ProductHandler(
            SoapDocumentProcessingUseCase soapDocumentUseCase,
            ObjectProvider<S3DocumentProcessingUseCase> s3DocumentUseCaseProvider,
            SyncDocumentsUseCase syncDocumentsUseCase,
            GetStatusUseCase getStatusUseCase,
            AnimalDocumentProcessingUseCase animalDocumentProcessingUseCase) {
        this.soapDocumentUseCase = soapDocumentUseCase;
        this.s3DocumentUseCaseProvider = s3DocumentUseCaseProvider;
        this.syncDocumentsUseCase = syncDocumentsUseCase;
        this.getStatusUseCase = getStatusUseCase;
        this.animalDocumentProcessingUseCase = animalDocumentProcessingUseCase;
    }

    public Mono<ServerResponse> processPendingProducts(ServerRequest request) {
        var headers = request.headers().asHttpHeaders().toSingleValueMap();

        Context context = Context.of(
            TYPE_JOB, request.pathVariables().containsKey(TYPE_JOB) ? request.pathVariable(TYPE_JOB) : "default",
            HEADER_TRACE_ID, headers.getOrDefault(HEADER_TRACE_ID, UUID.randomUUID().toString()),
            HEADER_USE_CASE, headers.getOrDefault(HEADER_USE_CASE, "default")
        );

        return Mono.deferContextual(ctx -> {
            String useCase = ctx.get(TYPE_JOB);
            String traceId = ctx.get(HEADER_TRACE_ID);

            LOGGER.log(Level.INFO, "Starting pending documents processing, traceId: {0}, useCase: {1}", new Object[]{traceId, useCase});

            getProcessor(useCase).executePendingDocuments()
                .doOnNext(result -> LOGGER.log(Level.INFO, "Document processed: correlationId={0}, status={1}",
                    new Object[]{result.getCorrelationId(), result.getStatus()}))
                .doOnError(error -> LOGGER.log(Level.SEVERE, "Processing failed for traceId {0}: {1}", new Object[]{traceId, error.getMessage()}))
                .doOnComplete(() -> LOGGER.log(Level.INFO, "Pending documents processing completed for traceId: {0}", new Object[]{traceId}))
                .contextWrite(ctx)
                .subscribe();

            return ServerResponse.accepted()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("status", "OK", "message", "Document processing initiated"));
        }).contextWrite(context);
    }

    public Mono<ServerResponse> syncProducts(ServerRequest request) {
        var headers = request.headers().asHttpHeaders().toSingleValueMap();
        String lastProductId = request.queryParam("last_product_id")
                .or(() -> request.queryParam("lastProductId"))
                .orElse("");

        Context context = Context.of(
                TYPE_JOB, request.pathVariables().containsKey(TYPE_JOB) ? request.pathVariable(TYPE_JOB) : "default",
                HEADER_TRACE_ID, headers.getOrDefault(HEADER_TRACE_ID, UUID.randomUUID().toString()),
                HEADER_USE_CASE, headers.getOrDefault(HEADER_USE_CASE, "default"),
                HEADER_DATE_INIT, request.queryParam(ApiConstants.HEADER_DATE_INIT).orElse(""),
                HEADER_DATE_END, request.queryParam(ApiConstants.HEADER_DATE_END).orElse("")
        ).put(HEADER_PRODUCT_STATUS, request.queryParam(ApiConstants.HEADER_PRODUCT_STATUS).orElse(""))
         .put("last_product_id", lastProductId);

        return Mono.deferContextual(ctx -> {
            String traceId = ctx.get(HEADER_TRACE_ID);
            String useCase = ctx.get(HEADER_USE_CASE);
            String dateInitVal = ctx.get(ApiConstants.HEADER_DATE_INIT);
            String dateEndVal = ctx.get(ApiConstants.HEADER_DATE_END);
            String stateVal = ctx.get(ApiConstants.HEADER_PRODUCT_STATUS);
            String lastProductIdVal = ctx.get("last_product_id");

            LOGGER.log(Level.INFO, "Starting document sync, traceId: {0}, useCase: {1}, dateInit: {2}, dateEnd: {3}, state: {4}, lastProductId: {5}",
                    new Object[]{traceId, useCase, dateInitVal, dateEndVal, stateVal, lastProductIdVal});
            syncDocumentsUseCase.execute(useCase)
                .doOnError(error -> LOGGER.log(Level.SEVERE, "Document sync failed for traceId {0}: {1}", new Object[]{traceId, error.getMessage()}))
                .doOnSuccess(v -> LOGGER.log(Level.INFO, "Document sync completed for traceId: {0}", new Object[]{traceId}))
                .contextWrite(ctx)
                .subscribe();
            return ServerResponse.accepted()
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("1");
        }).contextWrite(context);
    }

    public Mono<ServerResponse> getSyncStatus(ServerRequest request) {
        var headers = request.headers().asHttpHeaders().toSingleValueMap();
        String traceId = headers.getOrDefault(HEADER_TRACE_ID, UUID.randomUUID().toString());
        String useCase = request.pathVariable(TYPE_JOB);

        Context context = Context.of(
                TYPE_JOB, useCase,
                HEADER_TRACE_ID, traceId,
                HEADER_USE_CASE, useCase,
                HEADER_DATE_INIT, request.queryParam(ApiConstants.HEADER_DATE_INIT).orElse(""),
                HEADER_DATE_END, request.queryParam(ApiConstants.HEADER_DATE_END).orElse("")
        ).put(HEADER_PRODUCT_STATUS, request.queryParam(ApiConstants.HEADER_PRODUCT_STATUS).orElse(""));

        return getStatusUseCase.getSyncStatus(useCase)
                .flatMap(status -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue(status))
                .contextWrite(context);
    }

    public Mono<ServerResponse> getProcessStatus(ServerRequest request) {
        var headers = request.headers().asHttpHeaders().toSingleValueMap();
        String traceId = headers.getOrDefault(HEADER_TRACE_ID, UUID.randomUUID().toString());
        String useCase = request.pathVariable(TYPE_JOB);

        return getStatusUseCase.getProcessStatus(useCase)
                .flatMap(status -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue(status));
    }

    public Mono<ServerResponse> processDailyAnimalProducts(ServerRequest request) {
        var headers = request.headers().asHttpHeaders().toSingleValueMap();
        
        Context context = Context.of(
            TYPE_JOB, "daily",
            HEADER_TRACE_ID, headers.getOrDefault(HEADER_TRACE_ID, UUID.randomUUID().toString()),
            HEADER_USE_CASE, "animal"
        );

        return Mono.deferContextual(ctx -> {
            String traceId = ctx.get(HEADER_TRACE_ID);
            LOGGER.log(Level.INFO, "Starting Daily Animal Processing, traceId: {0}", traceId);

            animalDocumentProcessingUseCase.executeAnimalProcessing()
                .doOnNext(response -> LOGGER.log(Level.INFO, "Animal Document Processed: file={0}, success={1}",
                    new Object[]{response.getFilename(), response.isSuccess()}))
                .doOnError(error -> LOGGER.log(Level.SEVERE, "Animal Daily processing failed for traceId {0}: {1}", new Object[]{traceId, error.getMessage()}))
                .doOnComplete(() -> LOGGER.log(Level.INFO, "Animal Daily processing completed for traceId: {0}", traceId))
                .contextWrite(ctx)
                .subscribe();

            return ServerResponse.accepted()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(java.util.Map.of("status", "OK", "message", "Daily Animal processing initiated"));
        }).contextWrite(context);
    }

    public Mono<ServerResponse> getAnimalProcessStatus(ServerRequest request) {
        return getStatusUseCase.getProcessStatus("Animal")
                .flatMap(status -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue(status));
    }

    AbstractDocumentProcessingUseCase<Document, DocumentHistoryDTO> getProcessor(String processorType) {
        return switch (processorType) {
            case ApiConstants.PROCESSOR_SOAP -> soapDocumentUseCase;
            case ApiConstants.PROCESSOR_S3 -> {
                S3DocumentProcessingUseCase s3UseCase = s3DocumentUseCaseProvider.getIfAvailable();
                if (s3UseCase == null) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                         "S3 processor not available - enable 's3' profile");
                }
                yield s3UseCase;
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                 "Unknown processor type: '" + processorType + "'. Valid values: soap, s3");
        };
    }
}