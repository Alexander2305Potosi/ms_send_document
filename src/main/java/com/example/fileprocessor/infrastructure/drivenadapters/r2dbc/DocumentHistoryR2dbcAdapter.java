package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.SaveHistoryCommand;
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
    public Mono<Void> saveHistory(SaveHistoryCommand command) {
        
        // Si no es exitoso, el 'resultado' será el código de error específico para mayor visibilidad
        String resultStatus = command.response().isSuccess() ? "SUCCESS" : command.response().getErrorCode();

        DocumentHistoryEntity entity = DocumentHistoryEntity.builder()
            .documentId(command.docId())
            .filename(command.filename())
            .operation(command.operation())
            .result(resultStatus)
            .errorCode(command.response().getErrorCode())
            .errorMessage(command.response().getMessage())
            .retry(command.retryCount())
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
