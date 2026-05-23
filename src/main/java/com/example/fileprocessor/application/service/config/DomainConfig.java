package com.example.fileprocessor.application.service.config;

import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.ProductLocalRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.service.RulesBussinesService;
import com.example.fileprocessor.domain.usecase.S3DocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SyncDocumentsUseCase;
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ProcessorsProperties.class)
public class DomainConfig {

    @Bean
    @ConditionalOnBean(SoapGateway.class)
    public SoapDocumentProcessingUseCase soapDocumentUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            HomologationRepository homologationRepository,
            ProcessorsProperties properties) {
        return new SoapDocumentProcessingUseCase(
            persistencePort,
            productRestGateway,
            soapGateway,
            new RulesBussinesService(properties.soap()),
            homologationRepository
        );
    }

    @Bean
    @ConditionalOnBean(S3Gateway.class)
    public S3DocumentProcessingUseCase s3DocumentUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            S3Gateway s3Gateway,
            ProcessorsProperties properties) {
        return new S3DocumentProcessingUseCase(
            persistencePort,
            productRestGateway,
            s3Gateway,
            new RulesBussinesService(properties.s3())
        );
    }

    @Bean
    public SyncDocumentsUseCase syncDocumentsUseCase(
            DocumentRepository documentRepository,
            ProductMasterRepository productMasterRepository,
            ProductRestGateway productRestGateway,
            ProductLocalRepository productLocalRepository) {
        return new SyncDocumentsUseCase(
            documentRepository,
            productMasterRepository,
            productRestGateway,
            productLocalRepository
        );
    }
}
