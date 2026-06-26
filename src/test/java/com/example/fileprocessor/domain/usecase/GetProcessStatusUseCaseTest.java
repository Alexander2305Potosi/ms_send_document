package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.StateCount;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProcessStatusUseCaseTest {

    @Mock
    private DocumentRepository documentRepository;

    private GetProcessStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetProcessStatusUseCase(documentRepository);
    }

    @Test
    void executeWhenNoApplicableDocumentsReturnsCompleted() {
        when(documentRepository.countDocumentsGroupedByStateToday(any(), any()))
                .thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute("retention", "trace-1"))
                .expectNext(ApiConstants.STATUS_COMPLETED)
                .verifyComplete();
    }

    @Test
    void executeWhenPendingDocumentsExistReturnsInProgress() {
        when(documentRepository.countDocumentsGroupedByStateToday(any(), any()))
                .thenReturn(Flux.just(
                        new StateCount("PENDING", 2L),
                        new StateCount("PROCESSED", 3L)
                ));

        StepVerifier.create(useCase.execute("retention", "trace-1"))
                .expectNext(ApiConstants.STATUS_IN_PROGRESS)
                .verifyComplete();
    }

    @Test
    void executeWhenInProgressDocumentsExistReturnsInProgress() {
        when(documentRepository.countDocumentsGroupedByStateToday(any(), any()))
                .thenReturn(Flux.just(
                        new StateCount("IN_PROGRESS", 1L),
                        new StateCount("PROCESSED", 3L)
                ));

        StepVerifier.create(useCase.execute("retention", "trace-1"))
                .expectNext(ApiConstants.STATUS_IN_PROGRESS)
                .verifyComplete();
    }

    @Test
    void executeWhenTechnicalFailuresExistAndNoPendingReturnsError() {
        when(documentRepository.countDocumentsGroupedByStateToday(any(), any()))
                .thenReturn(Flux.just(
                        new StateCount("FAILED", 2L),
                        new StateCount("PROCESSED", 3L)
                ));

        StepVerifier.create(useCase.execute("retention", "trace-1"))
                .expectNext(ApiConstants.STATUS_ERROR)
                .verifyComplete();
    }

    @Test
    void executeWhenOnlyProcessedDocumentsExistReturnsCompleted() {
        when(documentRepository.countDocumentsGroupedByStateToday(any(), any()))
                .thenReturn(Flux.just(
                        new StateCount("PROCESSED", 5L)
                ));

        StepVerifier.create(useCase.execute("retention", "trace-1"))
                .expectNext(ApiConstants.STATUS_COMPLETED)
                .verifyComplete();
    }

    @Test
    void executeWhenBusinessRuleOrNoSucursalAreIgnored() {
        when(documentRepository.countDocumentsGroupedByStateToday(any(), any()))
                .thenReturn(Flux.just(
                        new StateCount("NO_SUCURSAL", 5L),
                        new StateCount("PATTERN_MISMATCH", 3L), // standard business rule
                        new StateCount("ERR_DUPLICATED_DOC", 2L) // business rule
                ));

        StepVerifier.create(useCase.execute("retention", "trace-1"))
                .expectNext(ApiConstants.STATUS_COMPLETED) // Since all rows are ignored, totalApplicable = 0
                .verifyComplete();
    }
}
