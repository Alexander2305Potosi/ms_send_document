package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.FinalizeProcessingCommand;
import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
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
    public Mono<Void> saveHistory(FinalizeProcessingCommand command) {
        
        // Si no es exitoso, el 'resultado' será el código de error específico para mayor visibilidad
        String resultStatus = command.response().isSuccess() ? "SUCCESS" : command.response().getErrorCode();
        
        // El nombre del archivo en el historial solo se guarda si es un procesamiento de ZIP (o según reglas de negocio)
        String historyFileName = Boolean.TRUE.equals(command.document().isZip()) ? command.document().getName() : null;

        DocumentHistoryEntity entity = DocumentHistoryEntity.builder()
            .documentId(command.document().getId())
            .filename(historyFileName)
            .operation(command.document().getUseCase())
            .result(resultStatus)
            .errorCode(command.response().getErrorCode())
            .errorMessage(command.response().getMessage())
            .retry(command.nextRetryCount())
            .startedAt(toLocalDateTime(command.startTime()))
            .completedAt(toLocalDateTime(Instant.now()))
            .build();

        return springDataRepository.save(entity).then();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) return null;
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
