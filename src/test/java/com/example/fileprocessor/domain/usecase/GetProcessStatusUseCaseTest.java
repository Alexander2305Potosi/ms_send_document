package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.product.StateCount;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProcessStatusUseCaseTest {

    @Mock private DocumentRepository documentRepository;

    private GetProcessStatusUseCase useCase() {
        return new GetProcessStatusUseCase(documentRepository);
    }

    private void mockCounts(List<StateCount> counts) {
        when(documentRepository.countDocumentsGroupedByStateToday(any(), anyString()))
                .thenReturn(Flux.fromIterable(counts));
    }

    @Test
    void execute_whenNoDocumentsToday_returnsCompleted() {
        mockCounts(List.of());
        StepVerifier.create(useCase().execute("retention", "t1"))
                .assertNext(r -> assertEquals(ApiConstants.STATUS_COMPLETED, r)).verifyComplete();
    }

    @Test
    void execute_whenPendingDocuments_returnsInProgress() {
        mockCounts(List.of(new StateCount(ProcessingResultCodes.PENDING.name(), 5L)));
        StepVerifier.create(useCase().execute("retention", "t2"))
                .assertNext(r -> assertEquals(ApiConstants.STATUS_IN_PROGRESS, r)).verifyComplete();
    }

    @Test
    void execute_whenInProgress_returnsInProgress() {
        mockCounts(List.of(new StateCount(ProcessingResultCodes.IN_PROGRESS.name(), 3L)));
        StepVerifier.create(useCase().execute("retention", "t3"))
                .assertNext(r -> assertEquals(ApiConstants.STATUS_IN_PROGRESS, r)).verifyComplete();
    }

    @Test
    void execute_whenPendingAndInProgressActive_returnsInProgress() {
        mockCounts(List.of(
                new StateCount(ProcessingResultCodes.PENDING.name(), 2L),
                new StateCount(ProcessingResultCodes.IN_PROGRESS.name(), 2L)
        ));
        StepVerifier.create(useCase().execute("retention", "t_active"))
                .assertNext(r -> assertEquals(ApiConstants.STATUS_IN_PROGRESS, r)).verifyComplete();
    }

    @Test
    void execute_whenAllProcessedNoFailures_returnsCompleted() {
        mockCounts(List.of(new StateCount(ProcessingResultCodes.PROCESSED.name(), 10L)));
        StepVerifier.create(useCase().execute("retention", "t4"))
                .assertNext(r -> assertEquals(ApiConstants.STATUS_COMPLETED, r)).verifyComplete();
    }

    @Test
    void execute_whenTechnicalFailuresNoPending_returnsError() {
        mockCounts(List.of(
                new StateCount(ProcessingResultCodes.PROCESSED.name(), 8L),
                new StateCount(ProcessingResultCodes.FAILED.name(), 2L)
        ));
        StepVerifier.create(useCase().execute("retention", "t5"))
                .assertNext(r -> assertEquals(ApiConstants.STATUS_ERROR, r)).verifyComplete();
    }

    @Test
    void execute_whenTechnicalFailuresAndStillActive_returnsInProgress() {
        mockCounts(List.of(
                new StateCount(ProcessingResultCodes.IN_PROGRESS.name(), 2L),
                new StateCount(ProcessingResultCodes.FAILED.name(), 1L)
                ));
        StepVerifier.create(useCase().execute("retention", "t6"))
                .assertNext(r -> assertEquals(ApiConstants.STATUS_IN_PROGRESS, r)).verifyComplete();
    }

    @Test
    void execute_whenOnlyExcludedStates_returnsCompleted() {
        mockCounts(List.of(
                new StateCount(ProcessingResultCodes.ERR_DUPLICATED_DOC.name(), 5L),
                new StateCount(ProcessingResultCodes.NO_SUCURSAL.name(), 3L)
        ));
        StepVerifier.create(useCase().execute("retention", "t7"))
                .assertNext(r -> assertEquals(ApiConstants.STATUS_COMPLETED, r)).verifyComplete();
    }
}
