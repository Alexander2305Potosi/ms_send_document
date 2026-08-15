package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.StateCount;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.common.AbstractReactiveAdapterOperation;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentEntity;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;


@Component
public class DocumentR2dbcAdapter
        extends
        AbstractReactiveAdapterOperation<DocumentEntity, Document, Long, com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentRepository>
        implements DocumentRepository {

    /**
     * Constructor del adaptador de R2DBC para documentos.
     * <p>
     * Secuencia:
     * 1. Llama al constructor de la clase base AbstractReactiveAdapterOperation.
     * 2. Proporciona el repositorio, el mapeador de objetos y la clase entidad correspondiente.
     *
     * @param repository el repositorio R2DBC
     * @param mapper el mapeador de objetos
     */
    public DocumentR2dbcAdapter(
            com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentRepository repository,
            ObjectMapper mapper) {
        super(repository, mapper, d -> mapper.map(d, Document.class), DocumentEntity.class);
    }

    /**
     * Encuentra documentos por un estado específico y caso de uso para el día actual.
     * <p>
     * Secuencia:
     * 1. Ejecuta una consulta a través del método abstracto doQueryMany.
     * 2. El repositorio subyacente filtra por estado, caso de uso y fecha de inicio.
     *
     * @param state el estado del documento
     * @param useCase el caso de uso
     * @param startOfDay la fecha y hora de inicio del día
     * @return un Flux de documentos
     */
    @Override
    public Flux<Document> findByStateAndUseCaseToday(String state, String useCase, LocalDateTime startOfDay) {
        return doQueryMany(() -> repository.findByStateAndUseCaseToday(state, useCase, startOfDay));
    }

    /**
     * Encuentra documentos por múltiples estados y un caso de uso para el día actual.
     * <p>
     * Secuencia:
     * 1. Ejecuta una consulta a través del método doQueryMany.
     * 2. El repositorio subyacente filtra por los estados indicados en el array, caso de uso y fecha de inicio.
     *
     * @param estados arreglo de estados permitidos
     * @param useCase el caso de uso
     * @param startOfDay la fecha y hora de inicio del día
     * @return un Flux de documentos
     */
    public Flux<Document> findByStatesAndUseCaseToday(String[] estados, String useCase, LocalDateTime startOfDay) {
        return doQueryMany(() -> repository.findByStatesAndUseCaseToday(estados, useCase, startOfDay));
    }

    /**
     * Actualiza el estado y el contador de reintentos de un documento, validando su estado previo.
     * <p>
     * Secuencia:
     * 1. Busca la entidad del documento actual por su ID.
     * 2. Verifica si el estado actual de la entidad coincide con alguno de los estados esperados.
     * 3. Si no coincide, retorna un error de procesamiento.
     * 4. Si coincide, actualiza los campos de la entidad con los valores del objeto de dominio (estado, reintentos, fecha de actualización, etc.).
     * 5. Guarda la entidad actualizada en la base de datos y retorna 1.
     *
     * @param doc el documento con los nuevos datos
     * @param expectedStates los estados previos aceptables
     * @return un Mono con la cantidad de registros actualizados (1) o un error
     */
    @Override
    public Mono<Long> updateStateAndRetry(Document doc, String... expectedStates) {
        return repository.findById(doc.getId())
            .flatMap(entity -> {
                boolean stateMatches = false;
                if (expectedStates != null) {
                    for (String expectedState : expectedStates) {
                        if (expectedState != null && expectedState.equals(entity.getState())) {
                            stateMatches = true;
                            break;
                        }
                    }
                }
                
                if (!stateMatches) {
                    return Mono.error(new com.example.fileprocessor.domain.exception.ProcessingException(
                        "No se pudo actualizar el documento: el estado actual [" + entity.getState() + 
                        "] no coincide con los esperados " + java.util.Arrays.toString(expectedStates), 
                        "STATE_MISMATCH"));
                }

                // Map updates from domain aggregate
                entity.setState(doc.getState());
                entity.setRetryCount(doc.getRetryCountSafe());
                entity.setUpdatedAt(LocalDateTime.now());
                entity.setSyncMessage(doc.getSyncMessage());
                entity.setHomologationFolder(doc.getHomologationFolder());
                entity.setHomologationCountry(doc.getHomologationCountry());
                entity.setCategoriaHomologada(doc.getCategoriaHomologada());

                return repository.save(entity).thenReturn(1L);
            });
    }

    /**
     * Verifica la existencia de un documento por ID de producto e ID de documento.
     * <p>
     * Secuencia:
     * 1. Delega la consulta directamente al repositorio de R2DBC subyacente.
     *
     * @param productId el ID del producto
     * @param documentId el ID del documento
     * @return un Mono con un booleano que indica si existe
     */
    @Override
    public Mono<Boolean> existsByProductIdAndDocumentId(String productId, String documentId) {
        return repository.existsByProductIdAndDocumentId(productId, documentId);
    }

    // NUEVO
    /**
     * Cuenta la cantidad total de documentos creados durante el día para un caso de uso.
     * <p>
     * Secuencia:
     * 1. Delega la operación de conteo al repositorio subyacente, pasando la fecha y el caso de uso.
     *
     * @param startOfDay la fecha y hora de inicio del día
     * @param useCase el caso de uso a filtrar
     * @return un Mono con la cantidad de documentos
     */
    @Override
    public Mono<Long> countDocumentsCreatedToday(LocalDateTime startOfDay, String useCase) {
        return repository.countDocumentsCreatedToday(startOfDay, useCase);
    }

    // NUEVO
    /**
     * Agrupa y cuenta los documentos por su estado en el transcurso del día para un caso de uso.
     * <p>
     * Secuencia:
     * 1. Delega la consulta de agrupación y conteo al repositorio.
     *
     * @param startOfDay la fecha y hora de inicio del día
     * @param useCase el caso de uso a considerar
     * @return un Flux con el conteo de cada estado encontrado
     */
    @Override
    public Flux<StateCount> countDocumentsGroupedByStateToday(LocalDateTime startOfDay, String useCase) {
        return repository.countDocumentsGroupedByStateToday(startOfDay, useCase);
    }



    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(DocumentR2dbcAdapter.class.getName());
}
