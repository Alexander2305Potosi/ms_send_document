package com.example.fileprocessor.application.service.config;

import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.service.RulesBussinesService;
import com.example.fileprocessor.domain.usecase.S3DocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentProcessingUseCase;
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration
@EnableConfigurationProperties(ProcessorsProperties.class)
public class DomainConfig {

    @Bean
    public RulesBussinesGateway syncDocumentValidator(ProcessorsProperties properties) {
        ProcessorsProperties.ProcessorConfig syncConfig = new ProcessorsProperties.ProcessorConfig(
            Long.MAX_VALUE,
            properties.soap() != null ? properties.soap().filenamePattern() : ".*"
        );
        return new RulesBussinesService(syncConfig);
    }

    @Bean
    @ConditionalOnBean(SoapGateway.class)
    public SoapDocumentProcessingUseCase soapDocumentUseCase(
            DocumentRepository documentRepository,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            HomologationRepository homologationRepository,
            DocumentHistoryRepository historyRepository,
            TransactionalOperator transactionalOperator,
            ProcessorsProperties properties) {
        return new SoapDocumentProcessingUseCase(
            documentRepository,
            productRestGateway,
            soapGateway,
            homologationRepository,
            new RulesBussinesService(properties.soap()),
            historyRepository,
            transactionalOperator
        );
    }

    @Bean
    @ConditionalOnBean(S3Gateway.class)
    public S3DocumentProcessingUseCase s3DocumentUseCase(
            DocumentRepository documentRepository,
            ProductRestGateway productRestGateway,
            S3Gateway s3Gateway,
            DocumentHistoryRepository historyRepository,
            TransactionalOperator transactionalOperator,
            ProcessorsProperties properties) {
        return new S3DocumentProcessingUseCase(
            documentRepository,
            productRestGateway,
            s3Gateway,
            new RulesBussinesService(properties.s3()),
            historyRepository,
            transactionalOperator
        );
    }
    @Bean
    public com.example.fileprocessor.domain.usecase.SyncDocumentsUseCase syncDocumentsUseCase(
            com.example.fileprocessor.domain.port.out.ProductRepository productRepository,
            DocumentRepository documentRepository,
            ProductRestGateway productRestGateway) {
        return new com.example.fileprocessor.domain.usecase.SyncDocumentsUseCase(
            productRepository,
            documentRepository,
            productRestGateway
        );
    }
}
