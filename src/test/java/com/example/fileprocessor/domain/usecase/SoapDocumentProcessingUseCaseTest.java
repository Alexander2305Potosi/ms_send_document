package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.infrastructure.helpers.config.ProcessorSettings;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SoapDocumentProcessingUseCaseTest {

    @Mock
    private ProductDocumentRepository documentRepository;
    @Mock
    private SoapGateway soapGateway;
    @Mock
    private ProductRestGateway productRestGateway;

    private SoapDocumentProcessingUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SoapDocumentProcessingUseCase(
            documentRepository, soapGateway,
            new FileValidator(createFileValidationConfig()),
            productRestGateway
        );
    }

    private ProcessorSettings createFileValidationConfig() {
        ProcessorSettings config = new ProcessorSettings();
        config.setAllowedTypes("pdf,txt,csv");
        config.setMaxSize(10485760L);
        return config;
    }

    @Test
    void implementationName_shouldReturnSoap() {
        assertThat(useCase.implementationName()).isEqualTo("SOAP");
    }
}
