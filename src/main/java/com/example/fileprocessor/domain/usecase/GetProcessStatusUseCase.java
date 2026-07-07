package com.example.fileprocessor.domain.usecase;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.IN_PROGRESS;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.NO_SUCURSAL;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PENDING;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PROCESSED;

import com.example.fileprocessor.domain.entity.product.StateCount;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.LocalTime;

@RequiredArgsConstructor
public class GetProcessStatusUseCase {

    private final DocumentRepository documentRepository;

    public Mono<String> execute(String useCase, String traceId) {
        LocalDateTime startOfDay = LocalDateTime.now().with(LocalTime.MIN);

        return documentRepository.countDocumentsGroupedByStateToday(startOfDay, useCase)
                .collectList()
                .map(list -> {
                    long processed = 0;
                    long pending = 0;
                    long technicalFailures = 0;

                    for (var row : list) {
                        String state = row.getState();
                        long count = row.getTotal();

                        // isBusinessRule() incluye ERR_DUPLICATED_DOC; NO_SUCURSAL se excluye explícitamente
                        if (NO_SUCURSAL.name().equals(state)
                                || ProcessingResultCodes.isBusinessRule(state)) {
                            continue;
                        }

                        // Los estados activos/en progreso son PENDING e IN_PROGRESS
                        if (PENDING.name().equals(state)
                                || IN_PROGRESS.name().equals(state)) {
                            pending += count;
                        } else if (PROCESSED.name().equals(state)) {
                            processed += count;
                        } else {
                            technicalFailures += count;
                        }
                    }

                    long totalApplicable = processed + pending + technicalFailures;

                    if (totalApplicable == 0) {
                        return ApiConstants.STATUS_COMPLETED;
                    }
                    if (pending > 0) {
                        return ApiConstants.STATUS_IN_PROGRESS;
                    }
                    return (technicalFailures > 0)
                            ? ApiConstants.STATUS_ERROR
                            : ApiConstants.STATUS_COMPLETED;
                });
    }
}
