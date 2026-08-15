package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.IN_PROGRESS;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PENDING;

import com.example.fileprocessor.domain.entity.animal.AnimalDocument;
import com.example.fileprocessor.domain.entity.animal.AnimalDocumentHistoryDTO;
import com.example.fileprocessor.domain.port.out.PersistenceGateway;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.AnimalDocumentEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.AnimalDocumentHistoryEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.AnimalDocumentRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.AnimalDocumentHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * Adapter implementation for persisting animal documents and history under the 'esquema_animales' schema.
 */
@Component
@RequiredArgsConstructor
public class AnimalPersistenceR2dbcAdapter implements PersistenceGateway<AnimalDocument, AnimalDocumentHistoryDTO> {

    private final AnimalDocumentRepository documentRepository;
    private final AnimalDocumentHistoryRepository historyRepository;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Flux<AnimalDocument> findPendingDocumentsToday(String useCase, LocalDateTime startOfDay) {
        String[] estados = new String[] { PENDING.name(), IN_PROGRESS.name() };
        return documentRepository.findByStatesAndUseCaseToday(estados, useCase, startOfDay)
                .map(this::toDomainDocument);
    }

    @Override
    public Mono<Long> lockDocumentForProcessing(AnimalDocument doc, int currentRetry) {
        // 1. Buscamos el registro ÚNICO directamente por sus IDs
        return documentRepository.findByProductIdAndDocumentId(doc.getAnimalId(), doc.getDocumentId())
                .flatMap(entity -> resumeExistingDocument(entity, doc))
                // 2. SI NO LO ENCUENTRA (Mono vacío): Es un documento nuevo, lo insertamos
                .switchIfEmpty(Mono.defer(() -> insertNewDocument(doc, currentRetry)));
    }

    private Mono<Long> resumeExistingDocument(AnimalDocumentEntity entity, AnimalDocument doc) {
        var isPendingOrInProgress = entity.getState().equals(PENDING.name()) || entity.getState().equals(IN_PROGRESS.name());
        var isFromToday = entity.getCreatedAt().toLocalDate().isEqual(LocalDate.now());

        if (isPendingOrInProgress && isFromToday) {
            var realRetry = entity.getRetryCount() != null ? entity.getRetryCount() : 0;
            entity.setState(IN_PROGRESS.name());
            entity.setRetryCount(realRetry);
            entity.setUpdatedAt(LocalDateTime.now());

            return documentRepository.save(entity)
                    .doOnNext(saved -> {
                        doc.setId(saved.getId());
                        doc.setRetryCount(realRetry);
                    })
                    .thenReturn(1L);
        }
        // Si existe pero es de otro día o ya terminó (PROCESSED/FAILED), no lo tocamos
        return Mono.just(0L);
    }

    private Mono<Long> insertNewDocument(AnimalDocument doc, int currentRetry) {
        var newEntity = AnimalDocumentEntity.builder()
                .documentId(doc.getDocumentId())
                .productId(doc.getAnimalId())
                .name(doc.getName())
                .state(IN_PROGRESS.name())
                .isZip(doc.getIsZip())
                .useCase("Animal")
                .retryCount(currentRetry)
                .createdAt(LocalDateTime.now())
                .build();

        return documentRepository.save(newEntity)
                .doOnNext(saved -> doc.setId(saved.getId()))
                .thenReturn(1L);
    }

    @Override
    public Mono<Void> finalizeProcessingAtomically(AnimalDocumentHistoryDTO history) {
        var updateDb = updateDocumentState(history);

        if (Boolean.TRUE.equals(history.getIsZip())) {
            return updateDb.as(transactionalOperator::transactional).then();
        }

        var historyEntity = buildHistoryEntity(history);
        return updateDb.then(historyRepository.save(historyEntity)).then()
                .as(transactionalOperator::transactional)
                .then();
    }

    private Mono<Void> updateDocumentState(AnimalDocumentHistoryDTO history) {
        var initialState = IN_PROGRESS.name();
        return documentRepository.findById(history.getDocumentId())
                .flatMap(entity -> {
                    if (!initialState.equals(entity.getState())) {
                        return Mono.error(new com.example.fileprocessor.domain.exception.ProcessingException(
                                "No se pudo finalizar procesamiento: el estado actual [" + entity.getState() + "] no es IN_PROGRESS",
                                "STATE_MISMATCH"));
                    }
                    entity.setState(history.getState());
                    entity.setRetryCount(history.getBusinessRetryCount());
                    entity.setUpdatedAt(LocalDateTime.now());
                    entity.setSyncMessage(history.getSyncMessage());
                    entity.setHomologationFolder(history.getHomologationFolder());
                    entity.setHomologationCountry(history.getHomologationCountry());
                    entity.setCategoriaHomologada(history.getCategoriaHomologada());
                    return documentRepository.save(entity);
                }).then();
    }

    @Override
    public Mono<Void> saveHistory(AnimalDocumentHistoryDTO history) {
        var historyEntity = buildHistoryEntity(history);
        return historyRepository.save(historyEntity).then();
    }

    private AnimalDocumentHistoryEntity buildHistoryEntity(AnimalDocumentHistoryDTO history) {
        return AnimalDocumentHistoryEntity.builder()
                .documentId(history.getDocumentId())
                .filename(history.getFilename())
                .useCase(history.getUseCase())
                .result(history.getState())
                .syncStatus(history.getSyncStatus())
                .syncMessage(history.getSyncMessage())
                .retry(history.getRetryCount())
                .startedAt(history.getStartedAt())
                .completedAt(history.getCompletedAt())
                .build();
    }

    private AnimalDocument toDomainDocument(AnimalDocumentEntity entity) {
        return AnimalDocument.builder()
                .id(entity.getId())
                .documentId(entity.getDocumentId())
                .animalId(entity.getProductId())
                .name(entity.getName())
                .state(entity.getState())
                .syncMessage(entity.getSyncMessage())
                .isZip(entity.getIsZip())
                .useCase(entity.getUseCase())
                .originFolder(entity.getOriginFolder())
                .originCountry(entity.getOriginCountry())
                .homologationFolder(entity.getHomologationFolder())
                .homologationCountry(entity.getHomologationCountry())
                .categoriaHomologada(entity.getCategoriaHomologada())
                .sucursal(entity.getSucursal())
                .retryCount(entity.getRetryCount())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    @Override
    public Flux<com.example.fileprocessor.domain.entity.product.StateCount> countDocumentsGroupedByStateToday(LocalDateTime startOfDay) {
        return documentRepository.countDocumentsGroupedByStateToday(startOfDay, "Animal");
    }
}
