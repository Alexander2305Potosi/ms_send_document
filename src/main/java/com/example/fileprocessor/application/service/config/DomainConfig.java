package com.example.fileprocessor.application.service.config;

import com.example.fileprocessor.domain.port.out.DocumentValidationGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.service.DefaultDocumentValidationService;
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
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            ProcessorsProperties properties) {
        return new SoapDocumentProcessingUseCase(
            productRestGateway,
            soapGateway,
            new DefaultDocumentValidationService(properties.soap())
        );
    }

    @Bean
    @ConditionalOnBean(S3Gateway.class)
    public S3DocumentProcessingUseCase s3DocumentUseCase(
            ProductRestGateway productRestGateway,
            S3Gateway s3Gateway,
            ProcessorsProperties properties) {
        return new S3DocumentProcessingUseCase(
            productRestGateway,
            s3Gateway,
            new DefaultDocumentValidationService(properties.s3())
        );
    }
}
