package com.example.fileprocessor.infrastructure.config;

import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import com.example.fileprocessor.domain.usecase.FileValidator;
import com.example.fileprocessor.domain.usecase.LoadProductsUseCase;
import com.example.fileprocessor.domain.usecase.ProcessProductDocumentsUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public FileValidator fileValidator(FileValidationConfig config) {
        return new FileValidator(config);
    }

    @Bean
    public LoadProductsUseCase loadProductsUseCase(ProductRestGateway productGateway,
                                                  ProductRepository productRepository,
                                                  ProductDocumentRepository documentRepository) {
        return new LoadProductsUseCase(productGateway, productRepository, documentRepository);
    }

    @Bean
    public ProcessProductDocumentsUseCase processProductDocumentsUseCase(ProductDocumentRepository documentRepository,
                                                                      ExternalSoapGateway soapGateway,
                                                                      FileValidator fileValidator,
                                                                      SoapCommunicationLogRepository logRepository,
                                                                      FileValidationConfig validationConfig) {
        return new ProcessProductDocumentsUseCase(documentRepository, soapGateway, fileValidator, logRepository, validationConfig);
    }
}
