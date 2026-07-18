package com.example.fileprocessor.infrastructure.drivenadapters.soap;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

class AnimalSoapGatewayAdapterTest {

    @Test
    void testSendDelegatesToSoapGateway() {
        SoapGateway soapGatewayMock = mock(SoapGateway.class);
        AnimalSoapGatewayAdapter adapter = new AnimalSoapGatewayAdapter(soapGatewayMock);

        FileUploadRequest request = FileUploadRequest.builder().build();
        FileUploadResponse response = FileUploadResponse.builder().success(true).build();

        when(soapGatewayMock.send(request)).thenReturn(Flux.just(response));

        StepVerifier.create(adapter.send(request))
                .expectNext(response)
                .expectComplete()
                .verify();

        verify(soapGatewayMock, times(1)).send(request);
    }
}
