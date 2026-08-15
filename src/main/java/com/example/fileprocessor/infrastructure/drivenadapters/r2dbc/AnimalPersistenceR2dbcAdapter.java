package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.IN_PROGRESS;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PENDING;

import com.example.fileprocessor.domain.entity.animal.AnimalDocument;
import com.example.fileprocessor.domain.entity.animal.AnimalDocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.product.StateCount;
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

    /**
     * Finds documents that are in pending or in-progress state for a specific use case today.
     * <p>
     * Secuencia:
     * 1. Define los estados a buscar (PENDING, IN_PROGRESS).
     * 2. Consulta al repositorio por los documentos que coincidan con los estados, caso de uso y a partir del inicio del día.
     * 3. Mapea cada entidad recuperada a su representación de dominio.
     *
     * @param useCase the use case to filter by
     * @param startOfDay the start time of the current day
     * @return a Flux of pending {@link AnimalDocument}
     */
    @Override
    public Flux<AnimalDocument> findPendingDocumentsToday(String useCase, LocalDateTime startOfDay) {
        String[] estados = new String[] { PENDING.name(), IN_PROGRESS.name() };
        return documentRepository.findByStatesAndUseCaseToday(estados, useCase, startOfDay)
                .map(this::toDomainDocument);
    }

    /**
     * Attempts to lock a document for processing, either by resuming an existing one or inserting a new one.
     * <p>
     * Secuencia:
     * 1. Busca un registro existente por el ID del animal y el ID del documento.
     * 2. Si lo encuentra, intenta reanudar su procesamiento.
     * 3. Si no lo encuentra, inserta un nuevo documento en la base de datos.
     *
     * @param doc the document to lock
     * @param currentRetry the current retry count
     * @return a Mono emitting 1L if successfully locked or inserted, 0L otherwise
     */
    @Override
    public Mono<Long> lockDocumentForProcessing(AnimalDocument doc, int currentRetry) {
        // 1. Buscamos el registro ÚNICO directamente por sus IDs
        return documentRepository.findByProductIdAndDocumentId(doc.getAnimalId(), doc.getDocumentId())
                .flatMap(entity -> resumeExistingDocument(entity, doc))
                // 2. SI NO LO ENCUENTRA (Mono vacío): Es un documento nuevo, lo insertamos
                .switchIfEmpty(Mono.defer(() -> insertNewDocument(doc, currentRetry)));
    }

    /**
     * Resumes the processing of an existing document if it meets the criteria (pending/in-progress and created today).
     * <p>
     * Secuencia:
     * 1. Verifica si la entidad está en estado PENDING o IN_PROGRESS.
     * 2. Verifica si la fecha de creación corresponde al día de hoy.
     * 3. Si cumple, actualiza el estado a IN_PROGRESS y guarda la entidad.
     * 4. Si no cumple, retorna 0L sin modificar la entidad.
     *
     * @param entity the existing database entity
     * @param doc the domain document to update with saved data
     * @return a Mono emitting 1L if resumed, 0L otherwise
     */
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

    /**
     * Inserts a new document record into the database with IN_PROGRESS state.
     * <p>
     * Secuencia:
     * 1. Construye una nueva entidad {@link AnimalDocumentEntity} con estado IN_PROGRESS.
     * 2. Guarda la nueva entidad en la base de datos.
     * 3. Actualiza el ID del objeto de dominio con el generado por la base de datos.
     * 4. Retorna 1L indicando éxito.
     *
     * @param doc the document to insert
     * @param currentRetry the current retry count
     * @return a Mono emitting 1L on success
     */
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

    /**
     * Atomically finalizes the processing of a document by updating its state and saving its history.
     * <p>
     * Secuencia:
     * 1. Prepara la operación de actualización del estado del documento.
     * 2. Si el documento es un ZIP, solo ejecuta la actualización del estado de forma transaccional.
     * 3. Si no es un ZIP, construye la entidad del historial y la guarda junto con la actualización del estado, ambas en una sola transacción.
     *
     * @param history the history details containing the final state
     * @return an empty Mono
     */
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

    /**
     * Updates the state and other metadata of a document based on its processing history.
     * <p>
     * Secuencia:
     * 1. Busca el documento por su ID.
     * 2. Verifica que su estado actual sea IN_PROGRESS; si no, lanza una excepción.
     * 3. Actualiza el estado, reintentos, fechas y datos de homologación.
     * 4. Guarda los cambios en el repositorio.
     *
     * @param history the processing history containing update details
     * @return an empty Mono
     */
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

    /**
     * Saves only the processing history of a document.
     * <p>
     * Secuencia:
     * 1. Construye la entidad de historial usando los datos del DTO.
     * 2. Guarda la entidad en el repositorio correspondiente.
     * 3. Retorna un Mono vacío al finalizar.
     *
     * @param history the history details to save
     * @return an empty Mono
     */
    @Override
    public Mono<Void> saveHistory(AnimalDocumentHistoryDTO history) {
        var historyEntity = buildHistoryEntity(history);
        return historyRepository.save(historyEntity).then();
    }

    /**
     * Builds an {@link AnimalDocumentHistoryEntity} from a DTO.
     * <p>
     * Secuencia:
     * 1. Toma los campos del DTO.
     * 2. Mapea cada campo a su correspondiente en el builder de la entidad.
     * 3. Retorna la entidad construida.
     *
     * @param history the history DTO
     * @return the mapped entity
     */
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

    /**
     * Converts a database entity into a domain document model.
     * <p>
     * Secuencia:
     * 1. Toma los datos de la entidad persistida.
     * 2. Los asigna a las propiedades del objeto de dominio usando su constructor/builder.
     * 3. Retorna el objeto de dominio resultante.
     *
     * @param entity the database entity
     * @return the domain document
     */
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

    /**
     * Counts the number of documents grouped by their state for today.
     * <p>
     * Secuencia:
     * 1. Consulta al repositorio para agrupar y contar los documentos por estado.
     * 2. Filtra por la fecha de inicio proporcionada y el caso de uso "Animal".
     * 3. Retorna el resultado como un flujo de {@link StateCount}.
     *
     * @param startOfDay the start time of the current day
     * @return a Flux of state counts
     */
    @Override
    public Flux<StateCount> countDocumentsGroupedByStateToday(LocalDateTime startOfDay) {
        return documentRepository.countDocumentsGroupedByStateToday(startOfDay, "Animal");
    }
}
