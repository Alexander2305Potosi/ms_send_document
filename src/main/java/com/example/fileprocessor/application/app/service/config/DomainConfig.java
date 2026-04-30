package com.example.fileprocessor.application.app.service.config;

import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.service.DocumentValidator;
import com.example.fileprocessor.domain.usecase.S3DocumentProcessingUseCase;
import com.example.fileprocessor.domain.usecase.SoapDocumentProcessingUseCase;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public DocumentValidator documentValidator(
            com.example.fileprocessor.domain.port.out.BussinesParamsGateway bussinesParamsGateway) {
        return new DocumentValidator(bussinesParamsGateway);
    }

    // ============ SOAP Processor ============

    @Bean
    @ConditionalOnBean(SoapGateway.class)
    public SoapDocumentProcessingUseCase soapDocumentUseCase(
            ProductRestGateway productRestGateway,
            ObjectProvider<SoapGateway> soapGatewayProvider,
            DocumentValidator documentValidator) {
        SoapGateway soapGateway = soapGatewayProvider.getIfAvailable();
        if (soapGateway == null) {
            return null;
        }
        return new SoapDocumentProcessingUseCase(productRestGateway, soapGateway, documentValidator);
    }

    // ============ S3 Processor ============

    @Bean
    @ConditionalOnBean(S3Gateway.class)
    public S3DocumentProcessingUseCase s3DocumentUseCase(
            ProductRestGateway productRestGateway,
            ObjectProvider<S3Gateway> s3GatewayProvider,
            DocumentValidator documentValidator) {
        S3Gateway s3Gateway = s3GatewayProvider.getIfAvailable();
        if (s3Gateway == null) {
            return null;
        }
        return new S3DocumentProcessingUseCase(productRestGateway, s3Gateway, documentValidator);
    }
}
