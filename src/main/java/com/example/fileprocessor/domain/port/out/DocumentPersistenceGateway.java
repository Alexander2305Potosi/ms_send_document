package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.Document;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.FinalizeProcessingCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Facade para las operaciones de persistencia del ciclo de vida de los documentos.
 * Agrupa operaciones complejas y transaccionales para que el dominio quede puro.
 */
public interface DocumentPersistenceGateway {

    Mono<Long> resetStaleDocumentsToday(String useCase, LocalDateTime startOfDay, LocalDateTime threshold);

    Flux<Document> findPendingDocumentsToday(String useCase, LocalDateTime startOfDay);

    Mono<Long> lockDocumentForProcessing(Long docId, int currentRetryCount);

    Mono<FileUploadResponse> finalizeProcessingAtomically(FinalizeProcessingCommand command);
}
