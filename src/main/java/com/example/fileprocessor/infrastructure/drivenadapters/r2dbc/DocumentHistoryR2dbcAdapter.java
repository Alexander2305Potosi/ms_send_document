package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.DocumentUpdateCommand;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.DocumentHistoryEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class DocumentHistoryR2dbcAdapter implements DocumentHistoryRepository {

    private final com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.repository.DocumentHistoryRepository springDataRepository;

    @Override
    public Mono<Void> saveHistory(DocumentUpdateCommand command) {
        
        if (command.response() == null) {
            return Mono.empty();
        }

        String resultStatus;
        if (command.response().isSuccess()) {
            resultStatus = ProcessingResultCodes.SUCCESS.name();
        } else if (isBusinessRule(command.response().getErrorCode())) {
            resultStatus = ProcessingResultCodes.SKIPPED.name();
        } else {
            resultStatus = ProcessingResultCodes.ERROR.name();
        }
        
        // CONCATENACIÓN SOLICITADA: Adjuntamos el número de intento y el código al mensaje final
        String enrichedMessage = command.response().getMessage();
        if (!command.response().isSuccess()) {
            String attemptInfo = "[INTENTO " + command.nextRetryCount() + "/3] ";
            String codeInfo = command.response().getErrorCode() != null ? "CÓDIGO: " + command.response().getErrorCode() + " - " : "";
            enrichedMessage = attemptInfo + codeInfo + enrichedMessage;
        }

        String historyFileName = Boolean.TRUE.equals(command.document().isZip()) ? command.document().getName() : null;

        DocumentHistoryEntity entity = DocumentHistoryEntity.builder()
            .documentId(command.document().getId())
            .filename(historyFileName)
            .operation(command.document().getUseCase())
            .result(resultStatus)
            .errorCode(command.response().getErrorCode())
            .errorMessage(enrichedMessage) // <--- Mensaje enriquecido con contexto
            .retry(command.nextRetryCount())
            .startedAt(toLocalDateTime(command.startTime()))
            .completedAt(toLocalDateTime(Instant.now()))
            .build();

        return springDataRepository.save(entity).then();
    }

    private boolean isBusinessRule(String errorCode) {
        if (errorCode == null) return false;
        return java.util.Set.of(
            ProcessingResultCodes.SIZE_EXCEEDED.name(),
            ProcessingResultCodes.PATTERN_MISMATCH.name(),
            ProcessingResultCodes.INVALID_BASE64.name(),
            ProcessingResultCodes.EMPTY_CONTENT.name(),
            ProcessingResultCodes.DECOMPRESSION_ERROR.name(),
            ProcessingResultCodes.HOMOLOGATION_FAILED.name()
        ).contains(errorCode);
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) return null;
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
