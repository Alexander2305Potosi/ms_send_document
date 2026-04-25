package com.example.fileprocessor.infrastructure.rest.adapter;

import com.example.fileprocessor.domain.entity.DocumentInfo;
import com.example.fileprocessor.domain.port.out.DocumentRestGateway;
import com.example.fileprocessor.infrastructure.rest.config.DocumentRestProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class DocumentRestGatewayImpl implements DocumentRestGateway {

    private static final Logger log = LoggerFactory.getLogger(DocumentRestGatewayImpl.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE_REF =
        new ParameterizedTypeReference<Map<String, Object>>() {};

    private final WebClient webClient;
    private final DocumentRestProperties properties;

    public DocumentRestGatewayImpl(WebClient.Builder webClientBuilder,
                                     DocumentRestProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder
            .baseUrl(properties.endpoint())
            .build();
    }

    @Override
    public Mono<DocumentInfo> getDocument(String documentId, String traceId) {
        log.info("Fetching document {} from REST API, traceId: {}", documentId, traceId);

        return webClient.get()
            .uri(properties.documentPath() + "/{id}", documentId)
            .accept(MediaType.APPLICATION_JSON)
            .header("X-Trace-Id", traceId)
            .retrieve()
            .bodyToMono(MAP_TYPE_REF)
            .map(this::mapToDocumentInfo)
            .doOnNext(doc -> log.info("Document {} retrieved: {}", documentId, doc.getFilename()));
    }

    @Override
    public Flux<DocumentInfo> getAllDocuments(String traceId) {
        log.info("Fetching all documents from REST API, traceId: {}", traceId);

        return webClient.get()
            .uri(properties.documentsPath())
            .accept(MediaType.APPLICATION_JSON)
            .header("X-Trace-Id", traceId)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
            .map(list -> list.stream().map(this::mapToDocumentInfo).toList())
            .flatMapMany(Flux::fromIterable)
            .doOnNext(doc -> log.info("Document retrieved: {}", doc.getFilename()));
    }

    private DocumentInfo mapToDocumentInfo(Map<String, Object> json) {
        String contentBase64 = (String) json.get("content");
        byte[] content = contentBase64 != null
            ? Base64.getDecoder().decode(contentBase64)
            : new byte[0];

        Object sizeObj = json.get("size");
        long size = sizeObj instanceof Number ? ((Number) sizeObj).longValue() : (long) content.length;

        Object isZipObj = json.get("isZip");
        boolean isZip = isZipObj instanceof Boolean ? (Boolean) isZipObj : false;

        return DocumentInfo.builder()
            .documentId((String) json.get("documentId"))
            .filename((String) json.get("filename"))
            .content(content)
            .contentType((String) json.get("contentType"))
            .size(size)
            .isZip(isZip)
            .origin((String) json.get("origin"))
            .build();
    }
}