package com.example.fileprocessor.infrastructure.rest.mapper;

import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.infrastructure.rest.dto.FileUploadRequestDto;
import org.springframework.stereotype.Component;

@Component
public class FileDtoMapper {

    public FileData toDomain(FileUploadRequestDto request) {
        return new FileData(
            request.content(),
            request.filename(),
            request.size(),
            request.contentType(),
            request.traceId()
        );
    }
}
