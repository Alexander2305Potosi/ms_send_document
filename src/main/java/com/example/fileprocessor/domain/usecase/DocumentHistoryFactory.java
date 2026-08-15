package com.example.fileprocessor.domain.usecase;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.BUSINESS_REJECTION;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILED;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILURE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PENDING;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PROCESSED;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.UNKNOWN_ERROR;

import com.example.fileprocessor.domain.entity.product.BaseDocument;
import com.example.fileprocessor.domain.entity.product.BaseDocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.exception.ProcessingException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public final class DocumentHistoryFactory {

    private DocumentHistoryFactory() {
        // Prevent instantiation
    }

    public record ProcessingConclusion(String nextState, int nextRetryCount) {}

    /**
     * Calcula el estado final del procesamiento y la cantidad de reintentos acumulados.
     * 
     * Secuencia de decisión:
     * 1. Sin respuestas: Devuelve estado FAILED.
     * 2. Todo exitoso: Devuelve estado PROCESSED.
     * 3. Reglas de negocio: Si hay un error de negocio (ej. archivo inválido), devuelve BUSINESS_REJECTION.
     * 4. Errores transitorios (Retry): Si hubo timeout/caída y no se superó MAX_RETRIES, devuelve PENDING sumando +1 al reintento.
     * 5. Cualquier otro caso o fin de reintentos: Devuelve FAILED.
     */
    public static ProcessingConclusion calculateNextState(int currentRetry, List<FileUploadResponse> responses) {
        ProcessingConclusion conclusion;
        if (responses.isEmpty()) {
            conclusion = new ProcessingConclusion(FAILED.name(), currentRetry);
        } else if (responses.stream().allMatch(FileUploadResponse::isSuccess)) {
            conclusion = new ProcessingConclusion(PROCESSED.name(), currentRetry);
        } else if (responses.stream().anyMatch(r -> ProcessingResultCodes.isBusinessRule(r.getSyncStatus()))) {
            conclusion = new ProcessingConclusion(BUSINESS_REJECTION.name(), currentRetry);
        } else if (currentRetry < ProcessingResultCodes.MAX_RETRIES && responses.stream().anyMatch(r -> ProcessingResultCodes.isTransient(r.getSyncStatus()))) {
            conclusion = new ProcessingConclusion(PENDING.name(), currentRetry + 1);
        } else {
            conclusion = new ProcessingConclusion(FAILED.name(), currentRetry);
        }
        return conclusion;
    }

    /**
     * Sincroniza y crea un DTO de historial para el resultado individual de la subida de un documento o archivo.
     * 
     * Secuencia:
     * 1. Clona el historial base.
     * 2. Obtiene el TraceID y lo añade al mensaje si está presente.
     * 3. Asigna ID, estado, intentos y fecha actualizadas al historial clonado.
     * 4. Si el documento original es un ZIP, conserva el nombre particular del archivo extraído.
     */
    public static <H extends BaseDocumentHistoryDTO> H syncHistoryDTO(BaseDocument doc, H fileHistory, FileUploadResponse response) {
        @SuppressWarnings("unchecked")
        H clonedHistory = (H) fileHistory.clone();
        
        String traceId = response.getTraceId();
        String syncMessage = response.getMessage();
        if (traceId != null && !traceId.isBlank() && !"unknown".equals(traceId)) {
            syncMessage = (syncMessage != null ? syncMessage : "") + " [TraceID: " + traceId + "]";
        }
        clonedHistory.setDocumentId(doc.getId());
        clonedHistory.setState(calculateFileState(response));
        clonedHistory.setUseCase(doc.getUseCase());
        clonedHistory.setRetryCount(response.getAttemptCount() > 0 ? response.getAttemptCount() : doc.getRetryCountSafe());
        clonedHistory.setBusinessRetryCount(doc.getRetryCountSafe());
        if (Boolean.TRUE.equals(doc.getIsZip())) {
            clonedHistory.setFilename(response.getFilename() != null ? response.getFilename() : clonedHistory.getFilename());
        } else {
            clonedHistory.setFilename(null);
        }
        clonedHistory.setSyncStatus(response.getSyncStatus());
        clonedHistory.setSyncMessage(syncMessage);
        clonedHistory.setCompletedAt(Instant.now());
        return clonedHistory;
    }

    /**
     * Sincroniza el historial a nivel "Global" o "Master" que impactará el documento principal.
     * 
     * Secuencia:
     * 1. Obtiene el estado de red representativo de las respuestas.
     * 2. Extrae un TraceID dominante de las respuestas.
     * 3. Agrupa o resuelve el mensaje final de sincronización dependiendo de si es un ZIP (múltiples) o no.
     * 4. Obtiene el mayor número de intentos utilizado entre todas las respuestas.
     * 5. Actualiza el objeto histórico padre con el estado global concluido y las estadísticas.
     */
    public static <H extends BaseDocumentHistoryDTO> H syncGlobalHistory(
            BaseDocument doc,
            H history,
            List<FileUploadResponse> responses,
            ProcessingConclusion conclusion) {
        String representativeStatus = responses.stream()
                .map(FileUploadResponse::getSyncStatus)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        String traceId = responses.stream()
                .map(FileUploadResponse::getTraceId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        String syncMessage;
        if (Boolean.TRUE.equals(doc.getIsZip())) {
            syncMessage = aggregateMessages(responses);
        } else {
            if (responses.isEmpty()) {
                syncMessage = "";
            } else {
                FileUploadResponse r = responses.getFirst();
                syncMessage = r.getMessage() != null ? r.getMessage() : (r.isSuccess() ? "SUCCESS" : r.getSyncStatus());
            }
            if (traceId != null && !traceId.isBlank() && !"unknown".equals(traceId)) {
                syncMessage = (syncMessage != null ? syncMessage : "") + " [TraceID: " + traceId + "]";
            }
        }

        int attempt = responses.stream()
                .map(FileUploadResponse::getAttemptCount)
                .max(Integer::compareTo)
                .orElse(0);

        history.setDocumentId(doc.getId());
        history.setState(conclusion.nextState());
        history.setUseCase(doc.getUseCase());
        history.setRetryCount(attempt > 0 ? attempt : doc.getRetryCountSafe());
        history.setBusinessRetryCount(conclusion.nextRetryCount());
        if (!Boolean.TRUE.equals(doc.getIsZip())) {
            history.setFilename(null);
        }
        history.setSyncStatus(representativeStatus);
        history.setSyncMessage(syncMessage);
        history.setCompletedAt(Instant.now());
        return history;
    }

    /**
     * Calcula el estado final del archivo en base al éxito o rechazo de negocio de la respuesta.
     * 
     * Secuencia:
     * 1. Si la respuesta es exitosa (success=true), devuelve PROCESSED.
     * 2. Si el código de estado indica un rechazo de negocio, devuelve BUSINESS_REJECTION.
     * 3. De lo contrario, devuelve PENDING (estado por defecto para errores técnicos).
     */
    public static String calculateFileState(FileUploadResponse response) {
        if (response.isSuccess()) {
            return PROCESSED.name();
        }
        if (ProcessingResultCodes.isBusinessRule(response.getSyncStatus())) {
            return BUSINESS_REJECTION.name();
        }
        return PENDING.name();
    }

    /**
     * Une y formatea los mensajes individuales de múltiples respuestas en un solo bloque de texto.
     * (Especialmente útil para reportar el resultado de múltiples archivos dentro de un ZIP).
     * 
     * Secuencia:
     * 1. Itera sobre cada FileUploadResponse.
     * 2. Extrae el nombre del archivo, TraceID y detalle (o "SUCCESS" si no hay mensaje).
     * 3. Combina todo en un string unificado separado por " || ".
     */
    public static String aggregateMessages(List<FileUploadResponse> responses) {
        return responses.stream()
                .map(r -> {
                    String filename = r.getFilename() != null ? r.getFilename() : "unknown";
                    String trace = (r.getTraceId() != null && !r.getTraceId().isBlank() && !"unknown".equals(r.getTraceId())) ? r.getTraceId() : "N/A";
                    String detail = r.getMessage() != null ? r.getMessage() : (r.isSuccess() ? "SUCCESS" : r.getSyncStatus());
                    return String.format("[Archivo: %s | TraceID: %s | Detalle: %s]", filename, trace, detail);
                })
                .collect(Collectors.joining(" || "));
    }

    /**
     * Procesa un error inesperado global y lo convierte en una respuesta de falla estandarizada.
     * 
     * Secuencia:
     * 1. Recorre la cadena de causas de la excepción buscando un ProcessingException original.
     * 2. Extrae el código de error (o UNKNOWN_ERROR si no lo tiene), el mensaje y el nombre de archivo afectado.
     * 3. Construye y retorna un FileUploadResponse marcado como fallido (success=false).
     */
    public static FileUploadResponse handleGlobalError(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root != root.getCause()) {
            if (root instanceof ProcessingException) {
                break;
            }
            root = root.getCause();
        }

        String syncStatus = UNKNOWN_ERROR.name();
        String message = root.getMessage();
        String filename = null;

        if (root instanceof ProcessingException pe) {
            syncStatus = pe.getErrorCode() != null && !pe.getErrorCode().isBlank()
                    ? pe.getErrorCode()
                    : UNKNOWN_ERROR.name();
            message = pe.getMessage();
            filename = pe.getFilename();
        }

        String finalMsg = message != null && !message.isBlank() ? message : error.getMessage();

        return FileUploadResponse.builder()
                .status(FAILURE.name())
                .syncStatus(syncStatus)
                .message(finalMsg != null && !finalMsg.isBlank() ? finalMsg : UNKNOWN_ERROR.value())
                .processedAt(Instant.now())
                .filename(filename)
                .success(false)
                .build();
    }

    /**
     * Convierte una excepción genérica en un ProcessingException enfocado en validación.
     * 
     * Secuencia:
     * 1. Verifica si la excepción ya es de tipo ProcessingException; si no, la envuelve.
     * 2. Asegura que tenga un código de error definido (fallback: UNKNOWN_ERROR).
     * 3. Si el documento original era un ZIP, asocia el nombre del archivo específico dentro del ZIP al error.
     */
    public static ProcessingException mapValidationError(Throwable e, BaseDocumentHistoryDTO masterHistory, BaseDocumentHistoryDTO innerHistory) {
        ProcessingException pe;
        if (e instanceof ProcessingException existingPe) {
            pe = existingPe;
            if (pe.getErrorCode() == null || pe.getErrorCode().isBlank()) {
                pe = new ProcessingException(pe.getMessage(),
                        UNKNOWN_ERROR.name(), pe.getCause());
            }
        } else {
            pe = new ProcessingException(e.getMessage(),
                    UNKNOWN_ERROR.name(), e);
        }
        if (Boolean.TRUE.equals(masterHistory.getIsZip())) {
            pe.setFilename(innerHistory.getFilename());
        }
        return pe;
    }
}
