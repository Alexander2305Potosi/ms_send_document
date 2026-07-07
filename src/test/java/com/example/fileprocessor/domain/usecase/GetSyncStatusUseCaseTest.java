package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSyncStatusUseCaseTest {

    @Mock
    private ProductMasterRepository productMasterRepository;

    @Mock
    private DocumentRepository documentRepository;

    private GetSyncStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetSyncStatusUseCase(productMasterRepository, documentRepository);
    }

    @Test
    void executeWhenLocalCountEqualsOrExceedsMasterCountReturnsCompleted() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.just(10L));
        when(documentRepository.countDocumentsCreatedToday(any(), any())).thenReturn(Mono.just(10L));

        StepVerifier.create(useCase.execute("retention", "trace-1"))
                .expectNext(ApiConstants.STATUS_COMPLETED)
                .verifyComplete();
    }

    @Test
    void executeWhenLocalCountLessThanMasterCountReturnsInProgress() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.just(10L));
        when(documentRepository.countDocumentsCreatedToday(any(), any())).thenReturn(Mono.just(5L));

        StepVerifier.create(useCase.execute("retention", "trace-1"))
                .expectNext(ApiConstants.STATUS_IN_PROGRESS)
                .verifyComplete();
    }

    @Test
    void executeWhenCountsAreEmptyReturnsCompleted() {
        when(productMasterRepository.countAllProducts()).thenReturn(Mono.empty());
        when(documentRepository.countDocumentsCreatedToday(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("retention", "trace-1"))
                .expectNext(ApiConstants.STATUS_COMPLETED) // 0 >= 0
                .verifyComplete();
    }
}
