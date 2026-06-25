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

    public DocumentR2dbcAdapter(
            com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentRepository repository,
            ObjectMapper mapper) {
        super(repository, mapper, d -> mapper.map(d, Document.class), DocumentEntity.class);
    }

    @Override
    public Flux<Document> findByStateAndUseCaseToday(String state, String useCase, LocalDateTime startOfDay) {
        return doQueryMany(() -> repository.findByStateAndUseCaseToday(state, useCase, startOfDay));
    }

    public Flux<Document> findByStatesAndUseCaseToday(String[] estados, String useCase, LocalDateTime startOfDay) {
        return doQueryMany(() -> repository.findByStatesAndUseCaseToday(estados, useCase, startOfDay));
    }

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

    @Override
    public Mono<Boolean> existsByProductIdAndDocumentId(String productId, String documentId) {
        return repository.existsByProductIdAndDocumentId(productId, documentId);
    }

    // NUEVO
    @Override
    public Mono<Long> countDocumentsCreatedToday(LocalDateTime startOfDay, String useCase) {
        return repository.countDocumentsCreatedToday(startOfDay, useCase);
    }

    // NUEVO
    @Override
    public Flux<StateCount> countDocumentsGroupedByStateToday(LocalDateTime startOfDay, String useCase) {
        return repository.countDocumentsGroupedByStateToday(startOfDay, useCase);
    }

    // Implementación del método de reanudación utilizando constantes límites.
    @Override
    public Mono<String> findLastProcessedProductIdInRange() {
        return Mono.deferContextual(ctx -> {
            String dateInitVal = ctx.getOrDefault(com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants.HEADER_DATE_INIT, "");
            String dateEndVal  = ctx.getOrDefault(com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants.HEADER_DATE_END,  "");

            // Parseo defensivo
            java.time.LocalDate start = com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants.parseDateOrToday(dateInitVal);
            java.time.LocalDate end   = com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants.parseDateOrToday(dateEndVal);

            // Conversión a LocalDateTime con las horas límite
            LocalDateTime startDateTime = start.atTime(com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants.START_OF_DAY_TIME);
            LocalDateTime endDateTime   = end.atTime(com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants.END_OF_DAY_TIME);

            LOGGER.info(() -> "Buscando último producto sincronizado en rango: ["
                    + startDateTime + " - " + endDateTime + "]");

            return repository.findLastProcessedProductIdInRange(startDateTime, endDateTime);
        });
    }

    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(DocumentR2dbcAdapter.class.getName());
}
