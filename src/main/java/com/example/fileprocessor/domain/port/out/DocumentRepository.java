package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.StateCount;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Port for managing document metadata and lifecycle states.
 */
public interface DocumentRepository {
    Mono<Document> save(Document document);

    /**
     * Finds documents for the current day.
     */
    Flux<Document> findByStateAndUseCaseToday(String state, String useCase, LocalDateTime startOfDay);

    /**
     * Updates document state and retry count.
     * @param doc The document with new values.
     * @param expectedState The previous state required for the update to succeed.
     */
    Mono<Long> updateStateAndRetry(Document doc, String expectedState);

    /**
     * Checks if a document with the given productId and documentId already exists.
     */
    Mono<Boolean> existsByProductIdAndDocumentId(String productId, String documentId);

    // NUEVO
    /** Cuenta documentos creados hoy por caso de uso (sync status) */
    Mono<Long> countDocumentsCreatedToday(LocalDateTime startOfDay, String useCase);

    // NUEVO
    /** Agrupa documentos por estado hoy por caso de uso (process status) */
    Flux<StateCount> countDocumentsGroupedByStateToday(LocalDateTime startOfDay, String useCase);

    /** Obtiene el último ID de producto procesado dentro del rango de fechas leído del contexto reactivo */
    Mono<String> findLastProcessedProductIdInRange();
}
