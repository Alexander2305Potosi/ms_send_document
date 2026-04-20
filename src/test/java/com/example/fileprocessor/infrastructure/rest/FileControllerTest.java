package com.example.fileprocessor.infrastructure.rest;

import com.example.fileprocessor.application.dto.FileUploadResponseDto;
import com.example.fileprocessor.application.usecase.ProcessFileUseCase;
import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.infrastructure.rest.controller.FileController;
import com.example.fileprocessor.infrastructure.rest.dto.FileUploadRequestDto;
import com.example.fileprocessor.infrastructure.rest.mapper.FileDtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private ProcessFileUseCase processFileUseCase;

    @Mock
    private FileDtoMapper fileDtoMapper;

    @Mock
    private ServerWebExchange exchange;

    @Mock
    private FilePart filePart;

    @InjectMocks
    private FileController fileController;

    @Test
    @SuppressWarnings("unchecked")
    void uploadFile_shouldReturnSuccessResponse() {
        byte[] content = "test content".getBytes(StandardCharsets.UTF_8);
        String filename = "test.pdf";
        String traceId = "test-trace-id";

        FileData fileData = new FileData(content, filename, content.length,
            "application/pdf", traceId);
        FileUploadResponseDto dto = new FileUploadResponseDto("SUCCESS", "File processed",
            "corr-123", traceId, java.time.Instant.now(), "ext-ref", true);

        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(content);

        // Setup FilePart mock
        when(filePart.content()).thenReturn(Flux.just(dataBuffer));
        when(filePart.filename()).thenReturn(filename);
        when(filePart.headers()).thenReturn(new org.springframework.http.HttpHeaders());

        // Setup multipart data
        MultiValueMap<String, Part> multipartData = mock(MultiValueMap.class);
        when(multipartData.getFirst("file")).thenReturn(filePart);

        // Setup exchange mock
        when(exchange.getMultipartData()).thenReturn(Mono.just(multipartData));

        // Setup mapper and usecase
        when(fileDtoMapper.toDomain(any(FileUploadRequestDto.class))).thenReturn(fileData);
        when(processFileUseCase.execute(any(FileData.class))).thenReturn(Mono.just(dto));

        StepVerifier.create(fileController.uploadFile(exchange))
            .assertNext(response -> {
                assert response.getStatusCode().is2xxSuccessful();
            })
            .verifyComplete();

        verify(processFileUseCase, times(1)).execute(any(FileData.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadFile_shouldReturnError_whenNoFileProvided() {
        // Setup multipart data with no file
        MultiValueMap<String, Part> multipartData = mock(MultiValueMap.class);
        when(multipartData.getFirst("file")).thenReturn(null);

        // Setup exchange mock
        when(exchange.getMultipartData()).thenReturn(Mono.just(multipartData));

        StepVerifier.create(fileController.uploadFile(exchange))
            .assertNext(response -> {
                assert response.getStatusCode().is5xxServerError();
            })
            .verifyComplete();
    }
}
