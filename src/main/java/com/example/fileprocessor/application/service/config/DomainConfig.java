package com.example.fileprocessor.application.service.config;

import com.example.fileprocessor.domain.port.out.DocumentTraceabilityGateway;
import com.example.fileprocessor.domain.port.out.ProductDbGateway;
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
            ProductDbGateway productDbGateway,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            DocumentTraceabilityGateway traceabilityGateway,
            ProcessorsProperties properties) {
        return new SoapDocumentProcessingUseCase(
            productDbGateway,
            productRestGateway,
            soapGateway,
            traceabilityGateway,
            new RulesBussinesService(properties.soap())
        );
    }

    @Bean
    @ConditionalOnBean(S3Gateway.class)
    public S3DocumentProcessingUseCase s3DocumentUseCase(
            ProductDbGateway productDbGateway,
            ProductRestGateway productRestGateway,
            S3Gateway s3Gateway,
            DocumentTraceabilityGateway traceabilityGateway,
            ProcessorsProperties properties) {
        return new S3DocumentProcessingUseCase(
            productDbGateway,
            productRestGateway,
            s3Gateway,
            traceabilityGateway,
            new RulesBussinesService(properties.s3())
        );
    }
}
