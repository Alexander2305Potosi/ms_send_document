package com.example.fileprocessor.infrastructure.config;

import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import com.example.fileprocessor.domain.port.out.DocumentRestGateway;
import com.example.fileprocessor.domain.port.out.DocumentRepository;
import com.example.fileprocessor.domain.usecase.FileValidator;
import com.example.fileprocessor.domain.usecase.ProcessFileUseCase;
import com.example.fileprocessor.domain.port.out.ExternalSoapGateway;
import com.example.fileprocessor.domain.port.out.SoapCommunicationLogRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {

    @Bean
    public FileValidator fileValidator(FileValidationConfig config) {
        return new FileValidator(config);
    }

    @Bean
    public ProcessFileUseCase processFileUseCase(DocumentRestGateway documentGateway,
                                                  DocumentRepository documentRepository,
                                                  ExternalSoapGateway soapGateway,
                                                  FileValidator fileValidator,
                                                  SoapCommunicationLogRepository logRepository,
                                                  FileValidationConfig validationConfig) {
        return new ProcessFileUseCase(documentGateway, documentRepository, soapGateway, fileValidator, logRepository, validationConfig);
    }
}
