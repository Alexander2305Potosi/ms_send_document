package com.example.fileprocessor.infrastructure.drivenadapters.restclient;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.port.out.AnimalRestGateway;
import com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto.DirectoryNode;
import com.example.fileprocessor.infrastructure.entrypoints.rest.config.AnimalRestProperties;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class AnimalRestGatewayAdapter implements AnimalRestGateway {

    private static final Logger LOGGER = Logger.getLogger(AnimalRestGatewayAdapter.class.getName());
    private static final Set<Integer> VALID_SOURCES = Set.of(1, 2, 4);

    private final WebClient webClient;
    private final AnimalRestProperties properties;

    public AnimalRestGatewayAdapter(WebClient.Builder webClientBuilder, AnimalRestProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        this.webClient = webClientBuilder
                .baseUrl(properties.endpoint())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public Flux<Document> getPendingDocumentsForAnimal(Long animalId) {
        return getDirectoryIdByAnimalId(animalId)
                .flatMap(this::getDirectoryTree)
                .flatMapMany(tree -> Flux.fromIterable(flattenAndFilter(tree)))
                .map(node -> Document.builder()
                        .documentId(node.getBusinessDocumentId())
                        .productId(node.getProductId())
                        .name(node.getName())
                        .isZip(false)
                        .useCase("Animal")
                        .retryCount(0)
                        .build())
                .onErrorResume(error -> {
                    LOGGER.log(Level.SEVERE, "Error obteniendo documentos para animalId={0}: {1}",
                            new Object[]{animalId, error.getMessage()});
                    return Flux.<Document>empty();
                });
    }

    private Mono<String> getDirectoryIdByAnimalId(Long animalId) {
        return webClient.get()
                .uri(properties.animalDirectoryPath(), animalId)
                .retrieve()
                .bodyToMono(DirectoryResponse.class)
                .map(DirectoryResponse::getDirectoryId)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .doOnError(e -> LOGGER.log(Level.SEVERE, "Error obteniendo directoryId para animalId={0}: {1}",
                        new Object[]{animalId, e.getMessage()}));
    }

    private Mono<DirectoryNode> getDirectoryTree(String directoryId) {
        return webClient.get()
                .uri(properties.directoryTreePath(), directoryId)
                .retrieve()
                .bodyToMono(DirectoryNode.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .doOnError(e -> LOGGER.log(Level.SEVERE, "Error obteniendo arbol para directoryId={0}: {1}",
                        new Object[]{directoryId, e.getMessage()}));
    }

    private List<DirectoryNode> flattenAndFilter(DirectoryNode root) {
        List<DirectoryNode> result = new ArrayList<>();
        traverse(root, result);
        return result;
    }

    private void traverse(DirectoryNode node, List<DirectoryNode> result) {
        if (node == null) return;
        if (node.getSource() != null && VALID_SOURCES.contains(node.getSource())) {
            result.add(node);
        }
        if (node.getChildren() != null) {
            node.getChildren().forEach(child -> traverse(child, result));
        }
    }

    @lombok.Data
    private static class DirectoryResponse {
        private String directoryId;
    }
}
