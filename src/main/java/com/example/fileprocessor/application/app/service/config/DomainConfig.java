package com.example.fileprocessor.application.app.service.config;

import com.example.fileprocessor.domain.port.out.CommunicationLogRepository;
import com.example.fileprocessor.domain.port.out.FileGateway;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.usecase.DocumentValidationRules;
import com.example.fileprocessor.domain.usecase.FileValidator;
import com.example.fileprocessor.domain.usecase.LoadProductsUseCase;
import com.example.fileprocessor.domain.usecase.ProcessingDependencies;
import com.example.fileprocessor.domain.usecase.ProductStatusAggregator;
import com.example.fileprocessor.domain.usecase.S3DocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentProcessingUseCase;
import com.example.fileprocessor.domain.valueobject.FolderExclusionRegexConfig;
import com.example.fileprocessor.infrastructure.helpers.config.ProcessorConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    // ============ Shared Components ============

    @Bean
    public ProcessingDependencies processingDependencies(
            ProductDocumentRepository documentRepository,
            ProductStatusAggregator statusAggregator,
            FileGateway fileGateway,
            CommunicationLogRepository logRepository) {
        return new ProcessingDependencies(
            documentRepository, statusAggregator, fileGateway, logRepository);
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

    // ============ SOAP Processor ============

    @Bean
    public FolderExclusionRegexConfig soapFolderExclusion(ProcessorConfig config) {
        return new FolderExclusionRegexConfig(config.getSoap().getFolderExclusionRegex());
    }

    @Bean
    public SoapDocumentProcessingUseCase soapDocumentUseCase(
            ProcessingDependencies deps,
            ProcessorConfig config) {
        FileValidator fileValidator = new FileValidator(config.getSoap());
        DocumentValidationRules validationRules = new DocumentValidationRules(config.getSoap());
        FolderExclusionRegexConfig folderRegex = soapFolderExclusion(config);
        return new SoapDocumentProcessingUseCase(
            deps, fileValidator, validationRules, folderRegex, config.getSoap());
    }

    // ============ S3 Processor ============

    @Bean
    public FolderExclusionRegexConfig s3FolderExclusion(ProcessorConfig config) {
        return new FolderExclusionRegexConfig(config.getS3().getFolderExclusionRegex());
    }

    @Bean
    public S3DocumentProcessingUseCase s3DocumentUseCase(
            ProcessingDependencies deps,
            ProcessorConfig config) {
        FileValidator fileValidator = new FileValidator(config.getS3());
        DocumentValidationRules validationRules = new DocumentValidationRules(config.getS3());
        FolderExclusionRegexConfig folderRegex = s3FolderExclusion(config);
        return new S3DocumentProcessingUseCase(
            deps, fileValidator, validationRules, folderRegex, config.getS3());
    }
}