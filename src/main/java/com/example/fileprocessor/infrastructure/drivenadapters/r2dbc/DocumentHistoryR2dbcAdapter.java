package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.BUSINESS_REJECTION;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.ERROR;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.ERR_DUPLICATED_DOC;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.IN_PROGRESS;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PROCESSED;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SKIPPED;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SUCCESS;

import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class DocumentHistoryR2dbcAdapter {

    private final DocumentHistoryRepository springDataRepository;

    /**
     * Saves the document processing history based on the provided DTO.
     * <p>
     * Secuencia:
     * 1. Evalúa el estado proporcionado en el DTO para determinar el estado final (resultStatus).
     * 2. Mapea los estados conocidos a sus equivalentes de éxito, salto, rechazo o error.
     * 3. Construye una entidad {@link DocumentHistoryEntity} usando los datos del DTO y el estado calculado.
     * 4. Guarda la entidad en la base de datos usando el repositorio de Spring Data.
     * 5. Retorna un Mono con la entidad guardada.
     *
     * @param historyDTO the data transfer object containing history details
     * @return a Mono emitting the saved {@link DocumentHistoryEntity}
     */
    public Mono<DocumentHistoryEntity> saveHistory(DocumentHistoryDTO historyDTO) {
        String resultStatus;
        
        if (IN_PROGRESS.name().equals(historyDTO.getState())) {
            resultStatus = IN_PROGRESS.name();
        } else {
            if (PROCESSED.name().equals(historyDTO.getState())) {
                resultStatus = SUCCESS.name();
            } else if (ERR_DUPLICATED_DOC.name().equals(historyDTO.getState())) {
                resultStatus = SKIPPED.name();
            } else if (BUSINESS_REJECTION.name().equals(historyDTO.getState())) {
                resultStatus = BUSINESS_REJECTION.name();
            } else {
                resultStatus = ERROR.name();
            }
        }

        DocumentHistoryEntity entity = DocumentHistoryEntity.builder()
                .documentId(historyDTO.getDocumentId())
                .useCase(historyDTO.getUseCase())
                .result(resultStatus)
                .syncStatus(historyDTO.getSyncStatus())
                .syncMessage(historyDTO.getSyncMessage())
                .retry(historyDTO.getRetryCount() != null ? historyDTO.getRetryCount() : 0)
                .startedAt(historyDTO.getStartedAt())
                .completedAt(historyDTO.getCompletedAt())
                .filename(historyDTO.getFilename())
                .build();

        return springDataRepository.save(entity);
    }
}
