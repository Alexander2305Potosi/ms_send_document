package com.example.fileprocessor.infrastructure.drivenadapters.soap;

import com.example.fileprocessor.domain.entity.AnimalUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.ProductUploadRequest;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnimalSoapGatewayAdapterTest {

    @Test
    void testSendDelegatesToSoapGateway() {
        SoapGateway soapGatewayMock = mock(SoapGateway.class);
        AnimalSoapGatewayAdapter adapter = new AnimalSoapGatewayAdapter(soapGatewayMock);

        // El adapter recibe un AnimalUploadRequest con campos específicos de animal
        AnimalUploadRequest animalRequest = AnimalUploadRequest.builder()
                .documentId("doc-1")
                .filename("animal.pdf")
                .animalId("animal-99")
                .raza("Labrador")
                .tipo("Perro")
                .build();

        FileUploadResponse response = FileUploadResponse.builder().success(true).build();

        // El adapter adapta internamente a ProductUploadRequest antes de delegar al SoapGateway
        when(soapGatewayMock.send(any(ProductUploadRequest.class))).thenReturn(Flux.just(response));

        StepVerifier.create(adapter.send(animalRequest))
                .expectNext(response)
                .expectComplete()
                .verify();

        verify(soapGatewayMock, times(1)).send(any(ProductUploadRequest.class));
    }
}
