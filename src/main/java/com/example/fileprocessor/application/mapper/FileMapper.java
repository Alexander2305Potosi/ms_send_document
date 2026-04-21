package com.example.fileprocessor.application.mapper;

import com.example.fileprocessor.application.dto.FileUploadResponseDto;
import com.example.fileprocessor.domain.entity.SoapResponse;
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
