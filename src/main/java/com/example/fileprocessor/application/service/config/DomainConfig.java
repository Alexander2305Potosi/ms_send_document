package com.example.fileprocessor.application.service.config;

import com.example.fileprocessor.domain.entity.animal.AnimalDocument;
import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.animal.AnimalDocumentHistoryDTO;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.MetadataStrategy;
import com.example.fileprocessor.domain.port.out.PersistenceGateway;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.ProductLocalRepository;
import com.example.fileprocessor.domain.port.out.ProductMasterRepository;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.port.out.AnimalSoapGateway;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.service.RulesBussinesService;
import com.example.fileprocessor.domain.usecase.S3DocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SyncDocumentsUseCase;
import com.example.fileprocessor.domain.usecase.GetStatusUseCase;
import com.example.fileprocessor.domain.usecase.AnimalDocumentProcessingUseCase;
import com.example.fileprocessor.domain.port.out.AnimalRepository;
import com.example.fileprocessor.domain.port.out.AnimalRestGateway;
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties;
import org.springframework.beans.factory.annotation.Qualifier;
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
            new RulesBussinesService<>(properties.soap()),
            homologationRepository,
            properties.zipTempDir()
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
            new RulesBussinesService<>(properties.s3()),
            properties.zipTempDir()
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

    @Bean
    public GetStatusUseCase getStatusUseCase(
            ProductMasterRepository productMasterRepository,
            DocumentRepository documentRepository) {
        return new GetStatusUseCase(productMasterRepository, documentRepository);
    }

    @Bean
    public AnimalDocumentProcessingUseCase animalDocumentProcessingUseCase(
            PersistenceGateway<AnimalDocument, AnimalDocumentHistoryDTO> animalPersistencePort,
            ProductRestGateway productRestGateway,
            AnimalRepository animalRepository,
            AnimalRestGateway animalRestGateway,
            AnimalSoapGateway soapGateway,
            HomologationRepository homologationRepository,
            @Qualifier("animalMetadataStrategy") MetadataStrategy animalMetadataStrategy,
            ProcessorsProperties properties) {
        return new AnimalDocumentProcessingUseCase(
            animalPersistencePort,
            productRestGateway,
            new RulesBussinesService<>(properties.animal()),
            properties.zipTempDir(),
            animalRepository,
            animalRestGateway,
            soapGateway,
            homologationRepository,
            animalMetadataStrategy
        );
    }
}
