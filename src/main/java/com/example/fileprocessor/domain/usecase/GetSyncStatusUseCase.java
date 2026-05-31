package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.time.LocalTime;

@RequiredArgsConstructor
public class GetSyncStatusUseCase {

    private final ProductMasterRepository productMasterRepository;
    private final DocumentRepository documentRepository;

    public Mono<String> execute(String useCase, String traceId) {
        LocalDateTime startOfDay = LocalDateTime.now().with(LocalTime.MIN);

        return Mono.zip(
                productMasterRepository.countAllProducts().defaultIfEmpty(0L),
                documentRepository.countDocumentsCreatedToday(startOfDay, useCase).defaultIfEmpty(0L)
        ).map(tuple -> {
            long masterCount = tuple.getT1();
            long localCount = tuple.getT2();
            return (localCount >= masterCount)
                    ? ApiConstants.STATUS_COMPLETED
                    : ApiConstants.STATUS_IN_PROGRESS;
        });
    }
}
