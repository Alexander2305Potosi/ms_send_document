package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
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
    public Mono<Void> saveHistory(Long docId, String filename, String operation, 
                                 FileUploadResponse response, Instant startTime) {
        
        // Si no es exitoso, el 'resultado' será el código de error específico para mayor visibilidad
        String resultStatus = response.isSuccess() ? "SUCCESS" : response.getErrorCode();

        DocumentHistoryEntity entity = DocumentHistoryEntity.builder()
            .documentId(docId)
            .filename(filename)
            .operation(operation)
            .result(resultStatus)
            .errorCode(response.getErrorCode())
            .errorMessage(response.getMessage())
            .retry(response.getAttemptCount())
            .startedAt(toLocalDateTime(startTime))
            .completedAt(toLocalDateTime(Instant.now()))
            .build();

        return springDataRepository.save(entity).then();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) return null;
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
