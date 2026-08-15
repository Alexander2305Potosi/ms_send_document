package com.example.fileprocessor.domain.usecase;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.EMPTY_CONTENT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILURE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PENDING;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.RETRYABLE_ERROR;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SUCCESS;

import com.example.fileprocessor.domain.entity.product.BaseDocument;
import com.example.fileprocessor.domain.entity.product.BaseDocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.product.ProcessingContext;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.PersistenceGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.util.ZipDecompressor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generic base use case for document processing.
 * Parametrized on T (BaseDocument) and H (BaseDocumentHistoryDTO).
 * Decoupled from direct database structure and client details.
 */
public abstract class AbstractDocumentProcessingUseCase<T extends BaseDocument, H extends BaseDocumentHistoryDTO> {

    protected final Logger LOGGER = Logger.getLogger(getClass().getName());
    private static final String DEFAULT_TRACE = "unknown-trace";
    private static final String TRACE_KEY = ProcessingException.HEADER_TRACE_ID;

    private final PersistenceGateway<T, H> persistencePort;
    private final RulesBussinesGateway<H> documentValidator;
    private final String tempDirPath;

    protected AbstractDocumentProcessingUseCase(
            PersistenceGateway<T, H> persistencePort,
            RulesBussinesGateway<H> documentValidator,
            String tempDirPath) {
        this.persistencePort = persistencePort;
        this.documentValidator = documentValidator;
        this.tempDirPath = tempDirPath;
    }

    public Flux<FileUploadResponse> executePendingDocuments() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        return Mono.deferContextual(ctx -> {
            String traceId = extractTraceId(ctx);
            LOGGER.log(Level.INFO, "[TraceID: {0}] Starting SEQUENTIAL execution for: {1}",
                    new Object[] { traceId, implementationName() });
            return Mono.just(traceId);
        }).flatMapMany(traceId -> getPendingDocuments(startOfDay)
                .collectList()
                .flatMapMany(Flux::fromIterable)
                .concatMap(doc -> processWithTracking(doc, traceId)));
    }

    protected abstract Flux<T> getPendingDocuments(LocalDateTime startOfDay);

    protected abstract H buildInitialHistory(T doc);

    protected abstract Mono<ProcessingContext<H>> downloadDocumentContent(H baseHistory);

    protected abstract Flux<FileUploadResponse> uploadDocument(ProcessingContext<H> context, Long docId);

    protected abstract H buildDecompressedEntryHistory(H zipHistory, String entryName);

    protected abstract String implementationName();

    /**
     * Flujo central para el procesamiento de un único documento.
     * 
     * Secuencia:
     * 1. Construye el historial inicial y solicita bloqueo (Lock) transaccional en la DB para prevenir carrera.
     * 2. Descarga el contenido desde el origen (ej. S3 o Sistema Local).
     * 3. Descomprime y valida las reglas (de ser ZIP, se obtienen múltiples archivos. Si no, solo el original).
     * 4. Sube cada archivo validado a su destino usando la concurrencia definida en uploadDocument.
     * 5. Asegura que el nombre del archivo no quede huérfano.
     * 6. Registra un intento de historial en la DB (para cada elemento del ZIP o si hubo error técnico).
     * 7. Concluye globalmente y define el futuro del registro en BD (processWithTracking -> concludeProcessing).
     * 8. Si durante la cadena salta una excepción general, se atrapa en handleGlobalErrorAndConclude para marcar error global.
     */
    protected Mono<FileUploadResponse> processWithTracking(T doc, String traceId) {
        H baseHistory = buildInitialHistory(doc);
        return persistencePort.lockDocumentForProcessing(doc, doc.getRetryCountSafe())
                .filter(rows -> rows > 0)
                .switchIfEmpty(Mono.defer(() -> {
                    LOGGER.log(Level.WARNING,
                            "[TraceID: {0}] Document {1} is already being processed or locked.",
                            new Object[] { traceId, doc.getDocumentId() });
                    return Mono.empty();
                }))
                .flatMap(unused -> downloadDocument(baseHistory)
                        .flatMap(masterContext -> decompressAndValidate(masterContext)
                                .flatMap(innerContext -> uploadDocument(innerContext, doc.getId())
                                        .map(resp -> ensureFilename(resp, innerContext.getHistory(), doc.getIsZip()))
                                        .flatMap(resp -> saveAttemptHistory(doc, innerContext.getHistory(), resp)), 1
                                )
                                .collectList()
                                .flatMap(responses -> concludeProcessing(doc, masterContext.getHistory(), responses, traceId))
                                .onErrorResume(error -> handleGlobalErrorAndConclude(error, doc, masterContext.getHistory(), traceId))
                        )
                )
                .onErrorResume(error -> handleGlobalErrorAndConclude(error, doc, baseHistory, traceId));
    }

    private Mono<ProcessingContext<H>> downloadDocument(H baseHistory) {
        return downloadDocumentContent(baseHistory);
    }

    private Flux<ProcessingContext<H>> decompressAndValidate(ProcessingContext<H> masterContext) {
        H masterHistory = masterContext.getHistory();
        return decompress(masterContext)
                .concatMap(ctx -> documentValidator.validate(ctx.getHistory(), true)
                        .map(h -> ctx.toBuilder().history(h).build())
                        .onErrorMap(e -> DocumentHistoryFactory.mapValidationError(e, masterHistory, ctx.getHistory())))
                .switchIfEmpty(Flux.defer(() -> {
                    if (Boolean.TRUE.equals(masterHistory.getIsZip())) {
                        return Flux.<ProcessingContext<H>>error(new ProcessingException(
                                EMPTY_CONTENT.value(),
                                EMPTY_CONTENT.name()));
                    }
                    return Flux.<ProcessingContext<H>>empty();
                }))
                .onErrorResume(ProcessingException.class, e -> {
                    if (ProcessingResultCodes.isBusinessRule(e.getErrorCode())) {
                        return Flux.error(e);
                    }
                    ProcessingException pe = new ProcessingException("Validation failed: " + e.getMessage(),
                            e.getErrorCode(), e);
                    pe.setFilename(e.getFilename() != null ? e.getFilename() : masterHistory.getFilename());
                    return Flux.error(pe);
                });
    }

    private FileUploadResponse ensureFilename(FileUploadResponse resp, H innerHistory, Boolean isZip) {
        if (resp.getFilename() == null) {
            return resp.toBuilder().filename(innerHistory.getFilename()).build();
        }
        return resp;
    }

    private Mono<FileUploadResponse> saveAttemptHistory(BaseDocument doc, H fileHistory, FileUploadResponse resp) {
        boolean shouldSave = Boolean.TRUE.equals(doc.getIsZip()) || resp.isTechnicalRetry();
        if (shouldSave) {
            return persistencePort.saveHistory(DocumentHistoryFactory.syncHistoryDTO(doc, fileHistory, resp)).thenReturn(resp);
        }
        return Mono.just(resp);
    }

    private List<FileUploadResponse> getFinalResponses(List<FileUploadResponse> responses) {
        java.util.Map<String, FileUploadResponse> finalMap = new java.util.LinkedHashMap<>();
        for (FileUploadResponse resp : responses) {
            String filename = resp.getFilename() != null ? resp.getFilename() : "unknown";
            finalMap.put(filename, resp);
        }
        return new java.util.ArrayList<>(finalMap.values());
    }

    /**
     * Define qué acción tomar tras recibir la lista de respuestas finalizadas.
     * 
     * Secuencia:
     * 1. Si está vacía (ej. archivo en blanco o corrompido), lanza error global.
     * 2. Reúne las respuestas de cada archivo evitando repetidos (getFinalResponses).
     * 3. Analiza si existió algún reintento técnico obligatorio (ej. timeout de red en algún pedazo del ZIP).
     * 4. Si hay "Technical Retry", solicita guardar el intento técnico en base de datos.
     * 5. Si no, consolida el estado final en base de datos (éxito, rechazo de negocio o fracaso total).
     */
    private Mono<FileUploadResponse> concludeProcessing(BaseDocument doc, H masterHistory, List<FileUploadResponse> responses, String traceId) {
        if (responses.isEmpty()) {
            return handleGlobalErrorAndConclude(
                    new ProcessingException("No files processed", EMPTY_CONTENT.name()),
                    doc, masterHistory, traceId);
        }

        List<FileUploadResponse> finalResponses = getFinalResponses(responses);
        FileUploadResponse representative = finalResponses.getFirst();
        boolean hasTechnicalRetry = finalResponses.stream().anyMatch(FileUploadResponse::isTechnicalRetry);

        if (hasTechnicalRetry) {
            return saveAuditOnly(doc, masterHistory, responses, traceId).thenReturn(representative);
        } else {
            return finalizeProcessing(doc, masterHistory, finalResponses, traceId).thenReturn(representative);
        }
    }

    /**
     * Captura y unifica todas las fallas que no hayan sido manejadas individualmente o que rompan todo el proceso.
     * (e.g. timeout en la descarga original del archivo desde S3).
     * 
     * Secuencia:
     * 1. Loggea el error y extrae el status sincrónico de la excepción (con handleGlobalError).
     * 2. Construye una respuesta (FileUploadResponse) de falla.
     * 3. Guarda el historial específico del error si era un archivo ZIP.
     * 4. Invoca 'finalizeProcessing' (o saveAuditOnly) para guardar este error permanentemente en el estado global del documento.
     */
    private Mono<FileUploadResponse> handleGlobalErrorAndConclude(Throwable error, BaseDocument doc, H masterHistory, String traceId) {
        LOGGER.log(Level.SEVERE, String.format("[TraceID: %s] Error processing document %s: %s", traceId, doc.getDocumentId(), error.getMessage()), error);
        FileUploadResponse response = DocumentHistoryFactory.handleGlobalError(error);
        if (error instanceof ProcessingException pe && pe.getFilename() != null) {
            response = response.toBuilder().filename(pe.getFilename()).build();
        }
        final FileUploadResponse finalResponse = response;

        final H errorFileHistoryDto;
        if (finalResponse.getFilename() != null) {
            masterHistory.setFilename(finalResponse.getFilename());
            masterHistory.setIsZip(false);
            errorFileHistoryDto = masterHistory;
        } else {
            errorFileHistoryDto = null;
        }

        Mono<Void> saveErrorHistory = Mono.empty();
        if (Boolean.TRUE.equals(doc.getIsZip()) && errorFileHistoryDto != null) {
            saveErrorHistory = persistencePort.saveHistory(
                    DocumentHistoryFactory.syncHistoryDTO(doc, errorFileHistoryDto, finalResponse)
            );
        }

        List<FileUploadResponse> responses = List.of(finalResponse);
        Mono<Void> finalizeMono = finalResponse.isTechnicalRetry()
                ? saveAuditOnly(doc, masterHistory, responses, traceId)
                : finalizeProcessing(doc, masterHistory, responses, traceId);

        return saveErrorHistory.then(finalizeMono).thenReturn(finalResponse);
    }

    /**
     * Efectúa la escritura atómica final en Base de Datos para el procesamiento regular (no technical retry).
     * 
     * Secuencia:
     * 1. Usa 'calculateNextState' para juzgar la Conclusión global del negocio.
     * 2. Define un prefijo para el log según si fue éxito o falla.
     * 3. Sincroniza el Historial Global llamando a DocumentHistoryFactory.
     * 4. Llama al adaptador de base de datos 'finalizeProcessingAtomically' para consolidar la transacción.
     */
    private Mono<Void> finalizeProcessing(BaseDocument doc, H history,
            List<FileUploadResponse> responses, String traceId) {
        int currentRetryCount = doc.getRetryCountSafe();
        DocumentHistoryFactory.ProcessingConclusion conclusion = DocumentHistoryFactory.calculateNextState(currentRetryCount, responses);

        String logPrefix;
        if (responses.stream().allMatch(FileUploadResponse::isSuccess)) {
            logPrefix = SUCCESS.name();
        } else if (PENDING.name().equals(conclusion.nextState())) {
            logPrefix = RETRYABLE_ERROR.name();
        } else {
            logPrefix = FAILURE.name();
        }

        H globalHistory = DocumentHistoryFactory.syncGlobalHistory(doc, history, responses, conclusion);

        LOGGER.log(Level.INFO, "[TraceID: {0}] [{1}] Document {2} (Product: {3}) -> {4}. Messages: {5}",
                new Object[] { traceId, logPrefix, doc.getDocumentId(), doc.getProductId(), conclusion.nextState(),
                        globalHistory.getSyncMessage() });

        return persistencePort
                .finalizeProcessingAtomically(globalHistory)
                .then();
    }

    /**
     * Registra en base de datos cuando se debe forzar un reintento técnico (e.g., Timeout).
     * 
     * Secuencia:
     * 1. Fuerza la conclusión al estado PENDING, agregando 1 al conteo de intentos.
     * 2. Sincroniza el historial global con este nuevo intento.
     * 3. Persiste de manera atómica para asegurar que el job de cron lo retome en el siguiente pase.
     */
    private Mono<Void> saveAuditOnly(BaseDocument doc, H history, List<FileUploadResponse> responses,
            String traceId) {
        int currentRetryCount = doc.getRetryCountSafe();
        LOGGER.log(Level.INFO, "[TraceID: {0}] Recording technical retry audit for Document {1} (Attempt {2})",
                new Object[] { traceId, doc.getDocumentId(), currentRetryCount });
        
        DocumentHistoryFactory.ProcessingConclusion conclusion = 
                new DocumentHistoryFactory.ProcessingConclusion(PENDING.name(), currentRetryCount + 1);
                
        H globalHistory = DocumentHistoryFactory.syncGlobalHistory(doc, history, responses, conclusion);
        
        return persistencePort.finalizeProcessingAtomically(globalHistory).then();
    }

    private Flux<ProcessingContext<H>> decompress(ProcessingContext<H> context) {
        H history = context.getHistory();
        if (!Boolean.TRUE.equals(history.getIsZip()) || history.getFilename() == null
                || history.getFilename().isBlank()) {
            return Flux.just(context);
        }
        return ZipDecompressor.decompress(context, tempDirPath, this::buildDecompressedEntryHistory);
    }

    private String extractTraceId(ContextView ctx) {
        return ctx.getOrDefault(TRACE_KEY, DEFAULT_TRACE);
    }
}