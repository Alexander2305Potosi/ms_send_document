package com.example.fileprocessor.application.service.config;

import com.example.fileprocessor.domain.port.out.DocumentHistoryRepository;
import com.example.fileprocessor.domain.port.out.ProductRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
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

@Configuration
@EnableConfigurationProperties(ProcessorsProperties.class)
public class DomainConfig {

    @Bean
    @ConditionalOnBean(SoapGateway.class)
    public SoapDocumentProcessingUseCase soapDocumentUseCase(
            ProductRepository productRepository,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            DocumentHistoryRepository historyRepository,
            ProcessorsProperties properties) {
        return new SoapDocumentProcessingUseCase(
            productRepository,
            productRestGateway,
            soapGateway,
            historyRepository,
            new RulesBussinesService(properties.soap())
        );
    }

    @Bean
    @ConditionalOnBean(S3Gateway.class)
    public S3DocumentProcessingUseCase s3DocumentUseCase(
            ProductRepository productRepository,
            ProductRestGateway productRestGateway,
            S3Gateway s3Gateway,
            DocumentHistoryRepository historyRepository,
            ProcessorsProperties properties) {
        return new S3DocumentProcessingUseCase(
            productRepository,
            productRestGateway,
            s3Gateway,
            historyRepository,
            new RulesBussinesService(properties.s3())
        );
    }
}