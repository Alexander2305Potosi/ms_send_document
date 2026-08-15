package com.example.fileprocessor.infrastructure.drivenadapters;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.IN_PROGRESS;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PENDING;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.product.StateCount;
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

    /**
     * Encuentra los documentos pendientes del día para un caso de uso específico.
     * <p>
     * Secuencia:
     * 1. Define los estados a buscar: PENDING e IN_PROGRESS.
     * 2. Llama al repositorio para buscar documentos que coincidan con los estados, caso de uso y fecha de inicio.
     *
     * @param useCase el caso de uso a filtrar
     * @param startOfDay la fecha y hora de inicio del día
     * @return un Flux de documentos pendientes o en progreso
     */
    @Override
    public Flux<Document> findPendingDocumentsToday(String useCase, LocalDateTime startOfDay) {
        String[] estados = new String[] {
            PENDING.name(),
            IN_PROGRESS.name()
        };
        return documentRepository.findByStatesAndUseCaseToday(estados, useCase, startOfDay);
    }

    /**
     * Bloquea un documento para su procesamiento, actualizando su estado.
     * <p>
     * Secuencia:
     * 1. Busca el documento existente por su ID.
     * 2. Obtiene la cantidad de reintentos actual de forma segura.
     * 3. Actualiza el estado del documento a IN_PROGRESS y le asigna la cantidad de reintentos.
     * 4. Guarda la actualización en la base de datos asegurando que los estados anteriores permitidos eran PENDING o IN_PROGRESS.
     *
     * @param doc el documento a procesar
     * @param currentRetry el número de reintento actual
     * @return un Mono con el número de filas afectadas (generalmente 1)
     */
    @Override
    public Mono<Long> lockDocumentForProcessing(Document doc, int currentRetry) {
        return documentRepository.findById(doc.getId())
                .flatMap(existingDoc -> {
                    int realRetry = existingDoc.getRetryCountSafe();
                    doc.setState(IN_PROGRESS.name());
                    doc.setRetryCount(realRetry);
                    
                    return documentRepository.updateStateAndRetry(doc, 
                            PENDING.name(), 
                            IN_PROGRESS.name());
                });
    }

    /**
     * Finaliza atómicamente el procesamiento de un documento y guarda su historial.
     * <p>
     * Secuencia:
     * 1. Define el estado inicial esperado (IN_PROGRESS).
     * 2. Construye un nuevo documento con los datos del historial (estado, reintentos, mensajes, etc.).
     * 3. Prepara la operación de actualización en el repositorio de documentos.
     * 4. Si el documento es un ZIP, omite el historial y ejecuta la transacción solo para el documento.
     * 5. En caso contrario, encadena la actualización del documento con el guardado del historial.
     * 6. Ejecuta ambas operaciones en una única transacción reactiva.
     *
     * @param history el historial del documento procesado
     * @return un Mono vacío al finalizar la transacción
     */
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

    /**
     * Guarda el historial de un documento.
     * <p>
     * Secuencia:
     * 1. Llama al repositorio de historial para guardar el registro.
     * 2. Retorna un Mono vacío indicando la finalización.
     *
     * @param history el historial a guardar
     * @return un Mono vacío
     */
    @Override
    public Mono<Void> saveHistory(DocumentHistoryDTO history) {
        return historyRepository.saveHistory(history).then();
    }

    /**
     * Cuenta los documentos del día agrupados por estado para el caso de uso SOAP.
     * <p>
     * Secuencia:
     * 1. Llama al repositorio para obtener el conteo agrupado por estado desde el inicio del día para el caso de uso fijo "SOAP".
     *
     * @param startOfDay la fecha y hora de inicio del día
     * @return un Flux con el conteo por estado
     */
    @Override
    public Flux<StateCount> countDocumentsGroupedByStateToday(LocalDateTime startOfDay) {
        return documentRepository.countDocumentsGroupedByStateToday(startOfDay, "SOAP");
    }
}
