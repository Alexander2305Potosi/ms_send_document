package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.port.out.CommunicationLogRepository;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.infrastructure.helpers.config.ProcessorSettings;
import com.example.fileprocessor.domain.port.out.FileGateway;
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
    private ProductStatusAggregator statusAggregator;
    @Mock
    private FileGateway fileGateway;
    @Mock
    private CommunicationLogRepository logRepository;

    private SoapDocumentProcessingUseCase useCase;
    private ProcessingDependencies deps;

    @BeforeEach
    void setUp() {
        deps = new ProcessingDependencies(documentRepository, statusAggregator, fileGateway, logRepository);

        ProcessorSettings settings = new ProcessorSettings();
        settings.setMaxConcurrency(5);
        settings.setMaxFileSizeMb(50);

        useCase = new SoapDocumentProcessingUseCase(
            deps,
            new FileValidator(createFileValidationConfig()),
            settings
        );
    }

    private ProcessorSettings createFileValidationConfig() {
        ProcessorSettings config = new ProcessorSettings();
        config.setAllowedTypes("pdf,txt,csv");
        config.setMaxSize(10485760L);
        config.setMaxFilenameLength(255);
        return config;
    }

    @Test
    void implementationName_shouldReturnSoap() {
        assertThat(useCase.getImplementationName()).isEqualTo("SOAP");
    }

    @Test
    void maxConcurrency_shouldReturnSettingsValue() {
        assertThat(useCase.maxConcurrency()).isEqualTo(5);
    }
}