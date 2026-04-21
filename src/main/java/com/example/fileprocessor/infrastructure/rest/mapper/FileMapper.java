package com.example.fileprocessor.infrastructure.rest.mapper;

import com.example.fileprocessor.domain.entity.SoapResponse;
import com.example.fileprocessor.infrastructure.rest.dto.FileUploadResponseDto;
import org.springframework.stereotype.Component;

@Component
public class FileMapper {

    public FileUploadResponseDto toResponseDto(SoapResponse soapResponse) {
        return new FileUploadResponseDto(
            soapResponse.status(),
            soapResponse.message(),
            soapResponse.correlationId(),
            soapResponse.traceId(),
            soapResponse.processedAt(),
            soapResponse.externalReference(),
            soapResponse.isSuccess()
        );
    }
}
