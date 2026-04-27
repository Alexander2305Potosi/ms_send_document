package com.example.fileprocessor.application.app.service.config;

import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import com.example.fileprocessor.domain.usecase.FileValidator;
import com.example.fileprocessor.domain.usecase.LoadProductsUseCase;
import com.example.fileprocessor.domain.usecase.S3DocumentUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentUseCase;
import com.example.fileprocessor.infrastructure.helpers.config.SoapProcessorProperties;
import com.example.fileprocessor.infrastructure.helpers.config.S3ProcessorProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public FileValidator soapFileValidator(SoapProcessorProperties properties) {
        return new FileValidator(properties);
    }

    @Bean
    public FileValidator s3FileValidator(S3ProcessorProperties properties) {
        return new FileValidator(properties);
    }

    @Bean
    public LoadProductsUseCase loadProductsUseCase(ProductRestGateway productGateway,
                                                  ProductRepository productRepository,
                                                  ProductDocumentRepository documentRepository) {
        return new LoadProductsUseCase(productGateway, productRepository, documentRepository);
    }

    @Bean
    public SoapDocumentUseCase soapDocumentUseCase(ProductDocumentRepository documentRepository,
                                                  ProductRepository productRepository,
                                                  ExternalSoapGateway soapGateway,
                                                  FileValidator soapFileValidator,
                                                  SoapCommunicationLogRepository logRepository,
                                                  SoapProcessorProperties validationConfig,
                                                  CircuitBreaker soapCircuitBreaker) {
        return new SoapDocumentUseCase(documentRepository, productRepository, soapGateway, soapFileValidator, logRepository, validationConfig, soapCircuitBreaker);
    }

    @org.springframework.context.annotation.Profile("s3")
    @Bean
    public S3DocumentUseCase s3DocumentUseCase(ProductDocumentRepository documentRepository,
                                              ProductRepository productRepository,
                                              S3Gateway s3Gateway,
                                              FileValidator s3FileValidator,
                                              SoapCommunicationLogRepository logRepository,
                                              S3ProcessorProperties validationConfig,
                                              CircuitBreaker s3CircuitBreaker) {
        return new S3DocumentUseCase(documentRepository, productRepository, s3Gateway, s3FileValidator, logRepository, validationConfig, s3CircuitBreaker);
    }
}