package com.example.fileprocessor.application.app.service.config;

import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.usecase.S3DocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentProcessingUseCase;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    // ============ SOAP Processor ============

    @Bean
    public SoapDocumentProcessingUseCase soapDocumentUseCase(
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway) {
        return new SoapDocumentProcessingUseCase(productRestGateway, soapGateway);
    }

    // ============ S3 Processor ============

    @Bean
    @ConditionalOnBean(S3Gateway.class)
    public S3DocumentProcessingUseCase s3DocumentUseCase(
            ProductRestGateway productRestGateway,
            ObjectProvider<S3Gateway> s3GatewayProvider) {
        S3Gateway s3Gateway = s3GatewayProvider.getIfAvailable();
        if (s3Gateway == null) {
            return null;
        }
        return new S3DocumentProcessingUseCase(productRestGateway, s3Gateway);
    }
}
