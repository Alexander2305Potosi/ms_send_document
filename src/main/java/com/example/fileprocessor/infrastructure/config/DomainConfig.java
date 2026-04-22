package com.example.fileprocessor.infrastructure.config;

import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import com.example.fileprocessor.domain.usecase.FileValidator;
import com.example.fileprocessor.domain.usecase.ProcessFileUseCase;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import com.example.fileprocessor.infrastructure.r2dbc.adapter.R2dbcSoapCommunicationLogRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.r2dbc.core.DatabaseClient;

@Configuration
public class DomainConfig {

    @Bean
    public FileValidator fileValidator(FileValidationConfig config) {
        return new FileValidator(config);
    }

    @Bean
    public ProcessFileUseCase processFileUseCase(ExternalSoapGateway soapGateway,
                                                  FileValidator fileValidator,
                                                  SoapCommunicationLogRepository logRepository) {
        return new ProcessFileUseCase(soapGateway, fileValidator, logRepository);
    }

    @Bean
    public SoapCommunicationLogRepository soapCommunicationLogRepository(
            DatabaseClient databaseClient) {
        return new R2dbcSoapCommunicationLogRepository(databaseClient);
    }
}
