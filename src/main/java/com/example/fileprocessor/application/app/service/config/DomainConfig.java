package com.example.fileprocessor.application.app.service.config;

import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.service.DocumentValidator;
import com.example.fileprocessor.domain.usecase.S3DocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentProcessingUseCase;
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ProcessorsProperties.class)
public class DomainConfig {

    // ============ SOAP Processor ============

    @Bean
    @ConditionalOnBean(SoapGateway.class)
    public SoapDocumentProcessingUseCase soapDocumentUseCase(
            ProductRestGateway productRestGateway,
            ObjectProvider<SoapGateway> soapGatewayProvider,
            ProcessorsProperties properties) {
        SoapGateway soapGateway = soapGatewayProvider.getIfAvailable();
        if (soapGateway == null) {
            return null;
        }
        return new SoapDocumentProcessingUseCase(
            productRestGateway, soapGateway, new DocumentValidator(properties.soap()));
    }

    // ============ S3 Processor ============

    @Bean
    @ConditionalOnBean(S3Gateway.class)
    public S3DocumentProcessingUseCase s3DocumentUseCase(
            ProductRestGateway productRestGateway,
            ObjectProvider<S3Gateway> s3GatewayProvider,
            ProcessorsProperties properties) {
        S3Gateway s3Gateway = s3GatewayProvider.getIfAvailable();
        if (s3Gateway == null) {
            return null;
        }
        return new S3DocumentProcessingUseCase(
            productRestGateway, s3Gateway, new DocumentValidator(properties.s3()));
    }
}
