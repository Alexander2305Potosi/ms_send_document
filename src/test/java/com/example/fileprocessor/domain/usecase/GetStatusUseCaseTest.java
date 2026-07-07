package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.StateCount;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetStatusUseCaseTest {

    @Mock
    private ProductMasterRepository productMasterRepository;

    @Mock
    private DocumentRepository documentRepository;

    private GetStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetStatusUseCase(productMasterRepository, documentRepository);
    }

    // --- Tests for getSyncStatus ---

    @Test
    void getSyncStatusWhenLocalCountEqualsOrExceedsMasterCountReturnsCompleted() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.just(10L));
        when(documentRepository.countDocumentsCreatedToday(any(), any())).thenReturn(Mono.just(10L));

        StepVerifier.create(useCase.getSyncStatus("retention"))
                .expectNext(ApiConstants.STATUS_COMPLETED)
                .verifyComplete();
    }

    @Test
    void getSyncStatusWhenLocalCountLessThanMasterCountReturnsInProgress() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.just(10L));
        when(documentRepository.countDocumentsCreatedToday(any(), any())).thenReturn(Mono.just(5L));

        StepVerifier.create(useCase.getSyncStatus("retention"))
                .expectNext(ApiConstants.STATUS_IN_PROGRESS)
                .verifyComplete();
    }

    @Test
    void getSyncStatusWhenCountsAreEmptyReturnsCompleted() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.empty());
        when(documentRepository.countDocumentsCreatedToday(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.getSyncStatus("retention"))
                .expectNext(ApiConstants.STATUS_COMPLETED) // 0 >= 0
                .verifyComplete();
    }

    // --- Tests for getProcessStatus ---

    @Test
    void getProcessStatusWhenNoApplicableDocumentsReturnsCompleted() {
        when(documentRepository.countDocumentsGroupedByStateToday(any(), any()))
                .thenReturn(Flux.empty());

        StepVerifier.create(useCase.getProcessStatus("retention"))
                .expectNext(ApiConstants.STATUS_COMPLETED)
                .verifyComplete();
    }

    @Test
    void getProcessStatusWhenPendingDocumentsExistReturnsInProgress() {
        when(documentRepository.countDocumentsGroupedByStateToday(any(), any()))
                .thenReturn(Flux.just(
                        new StateCount("PENDING", 2L),
                        new StateCount("PROCESSED", 3L)
                ));

        StepVerifier.create(useCase.getProcessStatus("retention"))
                .expectNext(ApiConstants.STATUS_IN_PROGRESS)
                .verifyComplete();
    }

    @Test
    void getProcessStatusWhenInProgressDocumentsExistReturnsInProgress() {
        when(documentRepository.countDocumentsGroupedByStateToday(any(), any()))
                .thenReturn(Flux.just(
                        new StateCount("IN_PROGRESS", 1L),
                        new StateCount("PROCESSED", 3L)
                ));

        StepVerifier.create(useCase.getProcessStatus("retention"))
                .expectNext(ApiConstants.STATUS_IN_PROGRESS)
                .verifyComplete();
    }

    @Test
    void getProcessStatusWhenTechnicalFailuresExistAndNoPendingReturnsError() {
        when(documentRepository.countDocumentsGroupedByStateToday(any(), any()))
                .thenReturn(Flux.just(
                        new StateCount("FAILED", 2L),
                        new StateCount("PROCESSED", 3L)
                ));

        StepVerifier.create(useCase.getProcessStatus("retention"))
                .expectNext(ApiConstants.STATUS_ERROR)
                .verifyComplete();
    }

    @Test
    void getProcessStatusWhenOnlyProcessedDocumentsExistReturnsCompleted() {
        when(documentRepository.countDocumentsGroupedByStateToday(any(), any()))
                .thenReturn(Flux.just(
                        new StateCount("PROCESSED", 5L)
                ));

        StepVerifier.create(useCase.getProcessStatus("retention"))
                .expectNext(ApiConstants.STATUS_COMPLETED)
                .verifyComplete();
    }

    @Test
    void getProcessStatusWhenBusinessRuleOrNoSucursalAreIgnored() {
        when(documentRepository.countDocumentsGroupedByStateToday(any(), any()))
                .thenReturn(Flux.just(
                        new StateCount("NO_SUCURSAL", 5L),
                        new StateCount("PATTERN_MISMATCH", 3L), // standard business rule
                        new StateCount("ERR_DUPLICATED_DOC", 2L) // business rule
                ));

        StepVerifier.create(useCase.getProcessStatus("retention"))
                .expectNext(ApiConstants.STATUS_COMPLETED) // Since all rows are ignored, totalApplicable = 0
                .verifyComplete();
    }
}
