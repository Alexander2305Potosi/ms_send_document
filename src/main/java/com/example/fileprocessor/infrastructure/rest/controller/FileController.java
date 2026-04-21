package com.example.fileprocessor.infrastructure.rest.controller;

import com.example.fileprocessor.application.dto.FileUploadResponseDto;
import com.example.fileprocessor.application.usecase.ProcessFileUseCase;
import com.example.fileprocessor.domain.entity.FileData;
import com.example.fileprocessor.infrastructure.rest.dto.FileUploadRequestDto;
import com.example.fileprocessor.infrastructure.rest.mapper.FileDtoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);
    private final ProcessFileUseCase processFileUseCase;
    private final FileDtoMapper fileDtoMapper;

    public FileController(ProcessFileUseCase processFileUseCase, FileDtoMapper fileDtoMapper) {
        this.processFileUseCase = processFileUseCase;
        this.fileDtoMapper = fileDtoMapper;
    }

    @GetMapping("/debug")
    public Mono<ResponseEntity<Map<String, Object>>> debugRequest(ServerWebExchange exchange) {
        String traceId = UUID.randomUUID().toString();
        log.info("Debug request received, headers: {}",
            exchange.getRequest().getHeaders().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ")));

        Map<String, Object> debug = Map.of(
            "method", exchange.getRequest().getMethod().name(),
            "path", exchange.getRequest().getPath().value(),
            "contentType", String.valueOf(exchange.getRequest().getHeaders().getContentType()),
            "headers", exchange.getRequest().getHeaders().toSingleValueMap(),
            "traceId", traceId
        );

        return Mono.just(ResponseEntity.ok(debug));
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<FileUploadResponseDto>> uploadFile(ServerWebExchange exchange) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        log.info("Receiving file upload request");
        log.info("Content-Type: {}", exchange.getRequest().getHeaders().getContentType());
        log.info("Headers: {}", exchange.getRequest().getHeaders());

        return exchange.getMultipartData()
            .doOnNext(parts -> {
                log.info("Multipart parts received: {}", parts.keySet());
                parts.forEach((k, v) -> log.info("Part {}: {}", k, v.getClass().getSimpleName()));
            })
            .flatMap(parts -> {
                Part filePart = parts.getFirst("file");
                if (filePart == null) {
                    log.error("No file part found. Available parts: {}", parts.keySet());
                    return Mono.error(new IllegalArgumentException("No file provided with key 'file'"));
                }
                if (!(filePart instanceof FilePart)) {
                    log.error("Part is not a FilePart, it's: {}", filePart.getClass().getName());
                    return Mono.error(new IllegalArgumentException("Part is not a file"));
                }

                FilePart file = (FilePart) filePart;
                log.info("Processing file: {}", file.filename());

                return DataBufferUtils.join(file.content())
                    .map(dataBuffer -> {
                        byte[] content = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(content);
                        DataBufferUtils.release(dataBuffer);

                        String contentType = file.headers().getContentType() != null
                            ? file.headers().getContentType().toString()
                            : "application/octet-stream";

                        return new FileUploadRequestDto(content, file.filename(),
                            contentType, content.length, traceId);
                    });
            })
            .map(fileDtoMapper::toDomain)
            .flatMap(fileData -> processFileUseCase.execute(fileData)
                .map(ResponseEntity::ok))
            .doFinally(signal -> MDC.remove("traceId"));
    }
}
