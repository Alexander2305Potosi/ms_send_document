package com.example.fileprocessor.infrastructure.drivenadapters;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.IN_PROGRESS;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PENDING;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.DocumentHistoryR2dbcAdapter;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.DocumentR2dbcAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.transaction.reactive.TransactionalOperator;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DocumentPersistenceAdapter implements DocumentPersistenceGateway {

    private final DocumentR2dbcAdapter documentRepository;
    private final DocumentHistoryR2dbcAdapter historyRepository;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Flux<Document> findPendingDocumentsToday(String useCase, LocalDateTime startOfDay) {
        String[] estados = new String[] {
            PENDING.name(),
            IN_PROGRESS.name()
        };
        return documentRepository.findByStatesAndUseCaseToday(estados, useCase, startOfDay);
    }

    @Override
    public Mono<Long> lockDocumentForProcessing(Document doc, int currentRetry) {
        doc.setState(IN_PROGRESS.name());
        doc.setRetryCount(currentRetry);
    
        // Camino rápido: doc viene de BD → ya tiene id → update directo
        if (doc.getId() != null) {
            return documentRepository.updateStateAndRetry(doc,
                    PENDING.name(),
                    IN_PROGRESS.name());
        }
    
        // Camino upsert: doc viene de REST API → sin id de BD todavía.
        return documentRepository.existsByProductIdAndDocumentId(
                        doc.getProductId(), doc.getDocumentId())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        // Existe → busca el Document de dominio (ya mapeado por el adapter)
                        return documentRepository
                                .findByStatesAndUseCaseToday(
                                        new String[]{ PENDING.name(), IN_PROGRESS.name() },
                                        doc.getUseCase(),
                                        LocalDateTime.now().minusDays(30))
                                .filter(found -> found.getDocumentId().equals(doc.getDocumentId()))
                                .next()
                                .flatMap(found -> {
                                    int realRetry = found.getRetryCount() != null ? found.getRetryCount() : 0;
                                    // Actualiza usando el objeto de dominio encontrado
                                    found.setState(IN_PROGRESS.name());
                                    found.setRetryCount(realRetry);
                                    return documentRepository.updateStateAndRetry(found,
                                                    PENDING.name(), IN_PROGRESS.name())
                                            .doOnNext(rows -> {
                                                doc.setId(found.getId());
                                                doc.setRetryCount(realRetry);
                                            });
                                })
                                .switchIfEmpty(Mono.just(0L));
                    } else {
                        // No existe → inserta como nuevo Document de dominio
                        Document newDoc = Document.builder()
                                .documentId(doc.getDocumentId())
                                .productId(doc.getProductId())
                                .name(doc.getName())
                                .state(IN_PROGRESS.name())
                                .isZip(doc.getIsZip())
                                .useCase(doc.getUseCase())
                                .retryCount(currentRetry)
                                .createdAt(LocalDateTime.now())
                                .build();
                        return documentRepository.save(newDoc)
                                .doOnNext(saved -> doc.setId(saved.getId()))
                                .thenReturn(1L);
                    }
                });
    }

    @Override
    public Mono<Void> finalizeProcessingAtomically(DocumentHistoryDTO history) {
        String initialState = IN_PROGRESS.name();
        
        Document doc = Document.builder()
                .id(history.getDocumentId())
                .state(history.getState())
                .retryCount(history.getBusinessRetryCount())
                .syncMessage(history.getSyncMessage())
                .homologationFolder(history.getHomologationFolder())
                .homologationCountry(history.getHomologationCountry())
                .categoriaHomologada(history.getCategoriaHomologada())
                .build();

        Mono<Void> updateDb = documentRepository.updateStateAndRetry(doc, initialState).then();
        
        // Para ZIPs no se guarda el resumen en historico_documentos porque la trazabilidad
        // individual de cada archivo interno ya fue registrada y la tabla documentos
        // almacena el estado consolidado.
        if (Boolean.TRUE.equals(history.getIsZip())) {
            return updateDb.as(transactionalOperator::transactional).then();
        }

        return updateDb.then(historyRepository.saveHistory(history))
                .as(transactionalOperator::transactional)
                .then();
    }

    @Override
    public Mono<Void> saveHistory(DocumentHistoryDTO history) {
        return historyRepository.saveHistory(history).then();
    }
}
