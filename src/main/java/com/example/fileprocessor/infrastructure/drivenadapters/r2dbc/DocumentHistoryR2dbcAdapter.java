package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.ProductState;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;
import com.example.fileprocessor.infrastructure.util.DateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class DocumentHistoryR2dbcAdapter implements DocumentHistoryRepository {

    private final com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentHistoryRepository springDataRepository;

    @Override
    public Mono<Void> saveHistory(com.example.fileprocessor.domain.entity.Document doc, com.example.fileprocessor.domain.entity.DocumentHistoryDTO historyDTO) {
        String resultStatus;
        String enrichedMessage;

        if (ProductState.IN_PROGRESS.equals(doc.getState())) {
            resultStatus = ProcessingResultCodes.IN_PROGRESS.name();
            enrichedMessage = historyDTO.getErrorMessage();
        } else {
            resultStatus = ProductState.PROCESSED.equals(doc.getState()) ? 
                          ProcessingResultCodes.SUCCESS.name() : ProcessingResultCodes.ERROR.name();
            
            // Re-apply business rule classification if needed
            if (ProcessingResultCodes.isBusinessRule(historyDTO.getErrorCode())) {
                resultStatus = ProcessingResultCodes.SKIPPED.name();
            }

            // ENRIQUECIMIENTO SOLICITADO: Intento + Código + Mensaje
            String attemptInfo = "[INTENTO " + doc.getRetryCountSafe() + "/3] ";
            String message = historyDTO.getErrorMessage();
            enrichedMessage = attemptInfo + message;
        }

        DocumentHistoryEntity entity = DocumentHistoryEntity.builder()
            .documentId(doc.getId())
            .filename(Boolean.TRUE.equals(doc.isZip()) ? doc.getName() : null)
            .operation(doc.getUseCase())
            .result(resultStatus)
            .errorCode(historyDTO.getErrorCode())
            .errorMessage(enrichedMessage)
            .retry(doc.getRetryCountSafe())
            .startedAt(DateMapper.toLocalDateTime(historyDTO.getStartedAt()))
            .completedAt(DateMapper.toLocalDateTime(historyDTO.getCompletedAt()))
            .stackTrace(historyDTO.getStackTrace())
            .build();

        return springDataRepository.save(entity).then();
    }

}
