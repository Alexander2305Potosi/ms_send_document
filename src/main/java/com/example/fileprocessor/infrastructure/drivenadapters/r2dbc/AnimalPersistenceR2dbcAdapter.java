package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.IN_PROGRESS;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PENDING;

import com.example.fileprocessor.domain.entity.product.Document;
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

/**
 * Adapter implementation for persisting animal documents and history under the 'esquema_animales' schema.
 */
@Component
@RequiredArgsConstructor
public class AnimalPersistenceR2dbcAdapter implements PersistenceGateway<Document, AnimalDocumentHistoryDTO> {

    private final AnimalDocumentRepository documentRepository;
    private final AnimalDocumentHistoryRepository historyRepository;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Flux<Document> findPendingDocumentsToday(String useCase, LocalDateTime startOfDay) {
        String[] estados = new String[] { PENDING.name(), IN_PROGRESS.name() };
        return documentRepository.findByStatesAndUseCaseToday(estados, useCase, startOfDay)
                .map(this::toDomainDocument);
    }

    @Override
    public Mono<Long> lockDocumentForProcessing(Document doc, int currentRetry) {
        return documentRepository.existsByProductIdAndDocumentId(doc.getProductId(), doc.getDocumentId())
                .flatMap(exists -> {
                    if (exists) {
                        return documentRepository.findByStatesAndUseCaseToday(new String[]{PENDING.name(), IN_PROGRESS.name()}, "Animal", LocalDateTime.now().minusDays(30))
                                .filter(entity -> entity.getDocumentId().equals(doc.getDocumentId()))
                                .next()
                                .flatMap(entity -> {
                                    entity.setState(IN_PROGRESS.name());
                                    entity.setRetryCount(currentRetry);
                                    entity.setUpdatedAt(LocalDateTime.now());
                                    return documentRepository.save(entity)
                                            .doOnNext(saved -> doc.setId(saved.getId()))
                                            .thenReturn(1L);
                                })
                                .switchIfEmpty(Mono.just(0L));
                    } else {
                        AnimalDocumentEntity newEntity = AnimalDocumentEntity.builder()
                                .documentId(doc.getDocumentId())
                                .productId(doc.getProductId())
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
                });
    }

    @Override
    public Mono<Void> finalizeProcessingAtomically(AnimalDocumentHistoryDTO history) {
        String initialState = IN_PROGRESS.name();

        Mono<Void> updateDb = documentRepository.findById(history.getDocumentId())
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

        if (Boolean.TRUE.equals(history.getIsZip())) {
            return updateDb.as(transactionalOperator::transactional).then();
        }

        AnimalDocumentHistoryEntity historyEntity = AnimalDocumentHistoryEntity.builder()
                .documentId(history.getDocumentId())
                .filename(history.getFilename())
                .useCase(history.getUseCase())
                .result(history.getState())
                .syncStatus(history.getState())
                .syncMessage(history.getSyncMessage())
                .retry(history.getRetryCount())
                .startedAt(history.getStartedAt())
                .completedAt(history.getCompletedAt())
                .build();

        return updateDb.then(historyRepository.save(historyEntity)).then()
                .as(transactionalOperator::transactional)
                .then();
    }

    @Override
    public Mono<Void> saveHistory(AnimalDocumentHistoryDTO history) {
        AnimalDocumentHistoryEntity historyEntity = AnimalDocumentHistoryEntity.builder()
                .documentId(history.getDocumentId())
                .filename(history.getFilename())
                .useCase(history.getUseCase())
                .result(history.getState())
                .syncStatus(history.getState())
                .syncMessage(history.getSyncMessage())
                .retry(history.getRetryCount())
                .startedAt(history.getStartedAt())
                .completedAt(history.getCompletedAt())
                .build();
        return historyRepository.save(historyEntity).then();
    }

    private Document toDomainDocument(AnimalDocumentEntity entity) {
        return Document.builder()
                .id(entity.getId())
                .documentId(entity.getDocumentId())
                .productId(entity.getProductId())
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
}
