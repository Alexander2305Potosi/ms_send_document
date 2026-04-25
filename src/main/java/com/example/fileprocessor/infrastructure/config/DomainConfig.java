package com.example.fileprocessor.infrastructure.config;

import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import com.example.fileprocessor.domain.usecase.AbstractProcessDocumentsUseCase;
import com.example.fileprocessor.domain.usecase.FileValidator;
import com.example.fileprocessor.domain.usecase.LoadProductsUseCase;
import com.example.fileprocessor.domain.usecase.S3DocumentUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

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
    @Profile("soap")
    public AbstractProcessDocumentsUseCase soapDocumentUseCase(ProductDocumentRepository documentRepository,
                                                               ExternalSoapGateway soapGateway,
                                                               FileValidator fileValidator,
                                                               SoapCommunicationLogRepository logRepository,
                                                               FileValidationConfig validationConfig) {
        return new SoapDocumentUseCase(documentRepository, soapGateway, fileValidator, logRepository, validationConfig);
    }

    @Bean
    @Profile("s3")
    public AbstractProcessDocumentsUseCase s3DocumentUseCase(ProductDocumentRepository documentRepository,
                                                              S3Gateway s3Gateway,
                                                              FileValidator fileValidator,
                                                              SoapCommunicationLogRepository logRepository,
                                                              FileValidationConfig validationConfig) {
        return new S3DocumentUseCase(documentRepository, s3Gateway, fileValidator, logRepository, validationConfig);
    }

    @Bean
    public AbstractProcessDocumentsUseCase defaultDocumentUseCase(ProductDocumentRepository documentRepository,
                                                                  ExternalSoapGateway soapGateway,
                                                                  FileValidator fileValidator,
                                                                  SoapCommunicationLogRepository logRepository,
                                                                  FileValidationConfig validationConfig) {
        return new SoapDocumentUseCase(documentRepository, soapGateway, fileValidator, logRepository, validationConfig);
    }
}
