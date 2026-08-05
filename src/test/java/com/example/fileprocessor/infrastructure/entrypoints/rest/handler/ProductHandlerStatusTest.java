package com.example.fileprocessor.infrastructure.entrypoints.rest.handler;

import com.example.fileprocessor.domain.usecase.GetStatusUseCase;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductHandlerStatusTest {

    @Mock private GetStatusUseCase getStatusUseCase;
    @Mock private ServerRequest serverRequest;

    private ProductHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ProductHandler(null, null, null, getStatusUseCase, null);
    }

    private void mockRequestHeaders() {
        ServerRequest.Headers headers = Mockito.mock(ServerRequest.Headers.class);
        when(headers.asHttpHeaders()).thenReturn(new HttpHeaders());
        when(serverRequest.headers()).thenReturn(headers);
    }

    @Test
    void getSyncStatusReturnsHttp200() {
        mockRequestHeaders();
        Mockito.lenient().when(serverRequest.pathVariable(ApiConstants.TYPE_JOB)).thenReturn("retention");
        when(getStatusUseCase.getSyncStatus(anyString()))
                .thenReturn(Mono.just(ApiConstants.STATUS_COMPLETED));

        StepVerifier.create(handler.getSyncStatus(serverRequest))
                .assertNext(res -> assertEquals(HttpStatus.OK, res.statusCode()))
                .verifyComplete();
    }

    @Test
    void getProcessStatusReturnsHttp200() {
        mockRequestHeaders();
        Mockito.lenient().when(serverRequest.pathVariable(ApiConstants.TYPE_JOB)).thenReturn("retention");
        when(getStatusUseCase.getProcessStatus(anyString()))
                .thenReturn(Mono.just(ApiConstants.STATUS_ERROR));

        StepVerifier.create(handler.getProcessStatus(serverRequest))
                .assertNext(res -> assertEquals(HttpStatus.OK, res.statusCode()))
                .verifyComplete();
    }
}
