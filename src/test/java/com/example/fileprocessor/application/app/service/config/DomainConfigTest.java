package com.example.fileprocessor.application.app.service.config;

import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.usecase.FileValidator;
import com.example.fileprocessor.domain.usecase.LoadProductsUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DomainConfigTest {

    @Mock
    private FileValidationConfig fileValidationConfig;

    @Mock
    private ProductRestGateway productGateway;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductDocumentRepository documentRepository;

    @Mock
    private ExternalSoapGateway soapGateway;

    @Mock
    private com.example.fileprocessor.domain.usecase.FileValidator fileValidator;

    @Mock
    private com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository logRepository;

    private DomainConfig domainConfig = new DomainConfig();

    @Test
    void fileValidator_shouldCreateWithConfig() {
        com.example.fileprocessor.domain.port.in.FileValidationConfig mockConfig =
            mock(com.example.fileprocessor.domain.port.in.FileValidationConfig.class);
        when(mockConfig.allowedTypes()).thenReturn("application/pdf,text/plain");

        FileValidator result = domainConfig.fileValidator(mockConfig);

        assertNotNull(result);
    }

    @Test
    void loadProductsUseCase_shouldCreateWithDependencies() {
        LoadProductsUseCase result = domainConfig.loadProductsUseCase(
            productGateway,
            productRepository,
            documentRepository
        );

        assertNotNull(result);
    }

    @Test
    void soapDocumentUseCase_shouldCreateWithDependencies() {
        SoapDocumentUseCase result = domainConfig.soapDocumentUseCase(
            documentRepository,
            productRepository,
            soapGateway,
            fileValidator,
            logRepository,
            fileValidationConfig
        );

        assertNotNull(result);
    }

    @Test
    void loadProductsUseCase_shouldNotBeNull() {
        LoadProductsUseCase useCase = domainConfig.loadProductsUseCase(
            productGateway,
            productRepository,
            documentRepository
        );

        assertNotNull(useCase);
    }
}