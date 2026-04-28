package com.example.fileprocessor.application.app.service.config;

import com.example.fileprocessor.domain.port.out.CommunicationLogRepository;
import com.example.fileprocessor.domain.port.out.FileGateway;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.usecase.DocumentProcessingOrchestrator;
import com.example.fileprocessor.domain.usecase.DocumentProcessingPipeline;
import com.example.fileprocessor.domain.usecase.DocumentSender;
import com.example.fileprocessor.domain.usecase.DocumentSenderImpl;
import com.example.fileprocessor.domain.usecase.DocumentSkipHandler;
import com.example.fileprocessor.domain.usecase.DocumentValidationRules;
import com.example.fileprocessor.domain.usecase.FileValidator;
import com.example.fileprocessor.domain.usecase.LoadProductsUseCase;
import com.example.fileprocessor.domain.usecase.ProductStatusAggregator;
import com.example.fileprocessor.infrastructure.helpers.config.ProcessorConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public FileValidator soapFileValidator(ProcessorConfig config) {
        return new FileValidator(config.getSoap());
    }

    @Bean
    public FileValidator s3FileValidator(ProcessorConfig config) {
        return new FileValidator(config.getS3());
    }

    @Bean
    public LoadProductsUseCase loadProductsUseCase(ProductRestGateway productGateway,
                                                  ProductRepository productRepository,
                                                  ProductDocumentRepository documentRepository) {
        return new LoadProductsUseCase(productGateway, productRepository, documentRepository);
    }

    @Bean
    public ProductStatusAggregator productStatusAggregator(ProductDocumentRepository documentRepository,
                                                           ProductRepository productRepository) {
        return new ProductStatusAggregator(documentRepository, productRepository);
    }

    @Bean
    public DocumentSkipHandler soapDocumentSkipHandler(ProductDocumentRepository documentRepository,
                                                      ProductStatusAggregator statusAggregator) {
        return new DocumentSkipHandler(documentRepository, statusAggregator);
    }

    @Bean
    public DocumentValidationRules soapDocumentValidationRules(ProcessorConfig config) {
        return new DocumentValidationRules(config.getSoap());
    }

    @Bean
    public DocumentValidationRules s3DocumentValidationRules(ProcessorConfig config) {
        return new DocumentValidationRules(config.getS3());
    }

    @Bean
    public DocumentSender documentSender(FileGateway fileGateway,
                                        CommunicationLogRepository logRepository) {
        return new DocumentSenderImpl(fileGateway, logRepository);
    }

    @Bean
    public DocumentProcessingPipeline documentPipeline(
            ProductDocumentRepository documentRepository,
            FileValidator soapFileValidator,
            DocumentValidationRules soapDocumentValidationRules,
            DocumentSkipHandler soapDocumentSkipHandler,
            ProductStatusAggregator productStatusAggregator,
            CircuitBreaker soapCircuitBreaker,
            DocumentSender documentSender) {
        return new DocumentProcessingPipeline(
            documentRepository,
            soapFileValidator,
            soapDocumentValidationRules,
            soapDocumentSkipHandler,
            productStatusAggregator,
            soapCircuitBreaker,
            documentSender);
    }

    @org.springframework.context.annotation.Profile("s3")
    @Bean
    public DocumentProcessingPipeline s3DocumentPipeline(
            ProductDocumentRepository documentRepository,
            FileValidator s3FileValidator,
            DocumentValidationRules s3DocumentValidationRules,
            DocumentSkipHandler soapDocumentSkipHandler,
            ProductStatusAggregator productStatusAggregator,
            CircuitBreaker s3CircuitBreaker,
            DocumentSender documentSender) {
        return new DocumentProcessingPipeline(
            documentRepository,
            s3FileValidator,
            s3DocumentValidationRules,
            soapDocumentSkipHandler,
            productStatusAggregator,
            s3CircuitBreaker,
            documentSender);
    }

    @Bean
    public DocumentProcessingOrchestrator soapDocumentOrchestrator(
            ProductDocumentRepository documentRepository,
            DocumentProcessingPipeline documentPipeline) {
        return new DocumentProcessingOrchestrator(
            documentRepository,
            documentPipeline,
            "SOAP");
    }

    @org.springframework.context.annotation.Profile("s3")
    @Bean
    public DocumentProcessingOrchestrator s3DocumentOrchestrator(
            ProductDocumentRepository documentRepository,
            DocumentProcessingPipeline s3DocumentPipeline) {
        return new DocumentProcessingOrchestrator(
            documentRepository,
            s3DocumentPipeline,
            "S3");
    }
}
