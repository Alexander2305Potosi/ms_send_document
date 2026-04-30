package com.example.fileprocessor.application.app.service.config;

import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.usecase.DocumentValidationService;
import com.example.fileprocessor.domain.usecase.LoadProductsUseCase;
import com.example.fileprocessor.domain.usecase.S3DocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.ZipProcessor;
import com.example.fileprocessor.domain.valueobject.FolderExclusionRegexConfig;
import com.example.fileprocessor.infrastructure.helpers.config.ProcessorConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    // ============ Load Products ============

    @Bean
    public LoadProductsUseCase loadProductsUseCase(
            ProductRestGateway productGateway,
            ProductRepository productRepository,
            ProductDocumentRepository documentRepository) {
        return new LoadProductsUseCase(productGateway, productRepository, documentRepository);
    }

    // ============ SOAP Processor ============

    @Bean
    public SoapDocumentProcessingUseCase soapDocumentUseCase(
            ProductDocumentRepository documentRepository,
            SoapGateway soapGateway,
            ProcessorConfig config,
            ProductRestGateway productRestGateway) {
        DocumentValidationService validationService = new DocumentValidationService(
            config.getSoap().getMaxSize(), config.getSoap().getAllowedTypes(), config.getSoap().getKeywords());
        return new SoapDocumentProcessingUseCase(
            documentRepository, productRestGateway, new ZipProcessor(validationService),
            soapGateway, validationService);
    }

    // ============ S3 Processor ============

    @Bean
    public FolderExclusionRegexConfig s3FolderExclusion(ProcessorConfig config) {
        return new FolderExclusionRegexConfig(config.getS3().getFolderExclusionRegex());
    }

    @Bean
    @ConditionalOnBean(S3Gateway.class)
    public S3DocumentProcessingUseCase s3DocumentUseCase(
            ProductDocumentRepository documentRepository,
            ObjectProvider<S3Gateway> s3GatewayProvider,
            ProcessorConfig config,
            ProductRestGateway productRestGateway) {
        S3Gateway s3Gateway = s3GatewayProvider.getIfAvailable();
        if (s3Gateway == null) {
            return null;
        }
        DocumentValidationService validationService = new DocumentValidationService(
            config.getS3().getMaxSize(), config.getS3().getAllowedTypes(), config.getS3().getKeywords());
        FolderExclusionRegexConfig folderRegex = s3FolderExclusion(config);
        return new S3DocumentProcessingUseCase(
            documentRepository, productRestGateway, new ZipProcessor(validationService),
            s3Gateway, validationService, folderRegex);
    }
}
