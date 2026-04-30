package com.example.fileprocessor.application.app.service.config;

import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.usecase.FileValidator;
import com.example.fileprocessor.domain.usecase.FolderInfoExtractor;
import com.example.fileprocessor.domain.usecase.LoadProductsUseCase;
import com.example.fileprocessor.domain.usecase.ProductStatusAggregator;
import com.example.fileprocessor.domain.usecase.S3DocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentProcessingUseCase;
import com.example.fileprocessor.domain.valueobject.FolderExclusionRegexConfig;
import com.example.fileprocessor.infrastructure.helpers.config.ProcessorConfig;
import com.example.fileprocessor.infrastructure.helpers.config.ProcessorSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

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
    public SoapDocumentProcessingUseCase soapDocumentUseCase(
            ProductDocumentRepository documentRepository,
            SoapGateway soapGateway,
            ProcessorConfig config,
            ProductRestGateway productRestGateway) {
        ProcessorSettings s = config.getSoap();
        FileValidator fileValidator = new FileValidator(s.getMaxSize(), s.getAllowedTypes());
        FolderInfoExtractor folderInfoExtractor = new FolderInfoExtractor(s.getKeywords());
        return new SoapDocumentProcessingUseCase(
            documentRepository, soapGateway, fileValidator, folderInfoExtractor, productRestGateway);
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
            S3Gateway s3Gateway,
            ProcessorConfig config,
            ProductRestGateway productRestGateway) {
        ProcessorSettings s = config.getS3();
        FileValidator fileValidator = new FileValidator(s.getMaxSize(), s.getAllowedTypes());
        FolderInfoExtractor folderInfoExtractor = new FolderInfoExtractor(s.getKeywords());
        FolderExclusionRegexConfig folderRegex = s3FolderExclusion(config);
        return new S3DocumentProcessingUseCase(
            documentRepository, s3Gateway, fileValidator, folderRegex, folderInfoExtractor, productRestGateway);
    }
}
