package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.product.Document;
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

    @Override
    public Mono<Long> updateStateAndRetry(Document doc, String expectedState) {
        return repository.findById(doc.getId())
            .flatMap(entity -> {
                // Atomic state validation
                if (!expectedState.equals(entity.getState())) {
                    return Mono.error(new com.example.fileprocessor.domain.exception.ProcessingException(
                        "No se pudo actualizar el documento: el estado actual [" + entity.getState() + 
                        "] no coincide con el esperado [" + expectedState + "]", 
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

}
