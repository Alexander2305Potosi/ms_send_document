package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSyncStatusUseCaseTest {

    @Mock private ProductMasterRepository productMasterRepository;
    @Mock private DocumentRepository documentRepository;

    private GetSyncStatusUseCase useCase() {
        return new GetSyncStatusUseCase(productMasterRepository, documentRepository);
    }

    @Test
    void execute_whenLocalLessThanMaster_returnsInProgress() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.just(10L));
        when(documentRepository.countDocumentsCreatedToday(any(), anyString())).thenReturn(Mono.just(5L));

        StepVerifier.create(useCase().execute("retention", "t1"))
                .assertNext(r -> assertEquals(ApiConstants.STATUS_IN_PROGRESS, r))
                .verifyComplete();
    }

    @Test
    void execute_whenLocalEqualsMaster_returnsCompleted() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.just(10L));
        when(documentRepository.countDocumentsCreatedToday(any(), anyString())).thenReturn(Mono.just(10L));

        StepVerifier.create(useCase().execute("retention", "t2"))
                .assertNext(r -> assertEquals(ApiConstants.STATUS_COMPLETED, r))
                .verifyComplete();
    }

    @Test
    void execute_whenLocalExceedsMaster_returnsCompleted() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.just(5L));
        when(documentRepository.countDocumentsCreatedToday(any(), anyString())).thenReturn(Mono.just(8L));

        StepVerifier.create(useCase().execute("retention", "t3"))
                .assertNext(r -> assertEquals(ApiConstants.STATUS_COMPLETED, r))
                .verifyComplete();
    }

    @Test
    void execute_whenBothZero_returnsCompleted() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.just(0L));
        when(documentRepository.countDocumentsCreatedToday(any(), anyString())).thenReturn(Mono.just(0L));

        StepVerifier.create(useCase().execute("retention", "t4"))
                .assertNext(r -> assertEquals(ApiConstants.STATUS_COMPLETED, r))
                .verifyComplete();
    }
}
