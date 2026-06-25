package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.product.Document;
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

    public Mono<DocumentHistoryEntity> saveHistory(DocumentHistoryDTO historyDTO) {
        String resultStatus;
        
        if (ProcessingResultCodes.IN_PROGRESS.name().equals(historyDTO.getState())) {
            resultStatus = ProcessingResultCodes.IN_PROGRESS.name();
        } else {
            if (ProcessingResultCodes.PROCESSED.name().equals(historyDTO.getState())) {
                resultStatus = ProcessingResultCodes.SUCCESS.name();
            } else if (ProcessingResultCodes.ERR_DUPLICATED_DOC.name().equals(historyDTO.getState())) {
                resultStatus = ProcessingResultCodes.SKIPPED.name();
            } else if (ProcessingResultCodes.BUSINESS_REJECTION.name().equals(historyDTO.getState())) {
                resultStatus = ProcessingResultCodes.BUSINESS_REJECTION.name();
            } else {
                resultStatus = ProcessingResultCodes.ERROR.name();
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
