package com.example.fileprocessor.infrastructure.config;

import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import com.example.fileprocessor.domain.usecase.FileValidator;
import com.example.fileprocessor.domain.usecase.ProcessFileUseCase;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public FileValidator fileValidator(FileValidationConfig config) {
        return new FileValidator(config);
    }

    @Bean
    public ProcessFileUseCase processFileUseCase(ExternalSoapGateway soapGateway,
                                                  FileValidator fileValidator) {
        return new ProcessFileUseCase(soapGateway, fileValidator);
    }
}
