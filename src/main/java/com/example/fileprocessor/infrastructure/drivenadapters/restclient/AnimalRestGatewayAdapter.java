package com.example.fileprocessor.infrastructure.drivenadapters.restclient;

import com.example.fileprocessor.domain.entity.animal.AnimalDocument;
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

    /**
     * Constructs the REST adapter for Animals.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Configura un cliente HTTP de Reactor con el timeout definido.</li>
     * <li>Construye el {@link WebClient} utilizando la URL base de las propiedades.</li>
     * <li>Almacena la instancia para las consultas de la API.</li>
     * </ol>
     * </p>
     *
     * @param webClientBuilder the builder
     * @param properties the properties
     */
    public AnimalRestGatewayAdapter(WebClient.Builder webClientBuilder, AnimalRestProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        this.webClient = webClientBuilder
                .baseUrl(properties.endpoint())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * Retrieves pending documents for a specific animal.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Obtiene el directoryId usando el animalId.</li>
     * <li>Recupera el árbol de directorios a partir del directoryId.</li>
     * <li>Aplana y filtra los nodos del árbol buscando documentos válidos.</li>
     * <li>Mapea cada nodo filtrado a un objeto {@link AnimalDocument}.</li>
     * <li>Si ocurre un error en cualquier paso, lo atrapa y devuelve un Flux vacío para continuar procesando.</li>
     * </ol>
     * </p>
     *
     * @param animalId the animal ID
     * @return a Flux of documents
     */
    @Override
    public Flux<AnimalDocument> getPendingDocumentsForAnimal(Long animalId) {
        return getDirectoryIdByAnimalId(animalId)
                .flatMap(this::getDirectoryTree)
                .flatMapMany(tree -> Flux.fromIterable(flattenAndFilter(tree)))
                .map(node -> AnimalDocument.builder()
                        .documentId(node.getBusinessDocumentId())
                        .animalId(node.getProductId())
                        .name(node.getName())
                        .isZip(false)
                        .useCase(AnimalDocument.USE_CASE_NAME)
                        .retryCount(0)
                        .build())
                .onErrorResume(error -> {
                    LOGGER.log(Level.SEVERE, "Error obteniendo documentos para animalId={0}: {1}",
                            new Object[]{animalId, error.getMessage()});
                    return Flux.<AnimalDocument>empty();
                });
    }

    /**
     * Calls the REST API to fetch a directory ID for the given animal ID.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Ejecuta una petición GET al path configurado usando el ID del animal.</li>
     * <li>Deserializa la respuesta como {@link DirectoryResponse}.</li>
     * <li>Extrae el ID del directorio y devuelve un error si está nulo.</li>
     * </ol>
     * </p>
     *
     * @param animalId the animal ID
     * @return a Mono emitting the directory ID
     */
    private Mono<String> getDirectoryIdByAnimalId(Long animalId) {
        return webClient.get()
                .uri(properties.animalDirectoryPath(), animalId)
                .retrieve()
                .bodyToMono(DirectoryResponse.class)
                .flatMap(resp -> resp.getDirectoryId() != null
                        ? Mono.just(resp.getDirectoryId())
                        : Mono.error(new IllegalStateException("DirectoryId es nulo para el animalId=" + animalId)))
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .doOnError(e -> LOGGER.log(Level.SEVERE, "Error obteniendo directoryId para animalId={0}: {1}",
                        new Object[]{animalId, e.getMessage()}));
    }

    /**
     * Calls the REST API to fetch a directory tree given a directory ID.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Realiza una petición GET al endpoint de árbol de directorio.</li>
     * <li>Deserializa la respuesta en un objeto {@link DirectoryNode} (nodo raíz).</li>
     * <li>Controla errores y timeouts registrándolos en los logs.</li>
     * </ol>
     * </p>
     *
     * @param directoryId the directory ID
     * @return a Mono emitting the root directory node
     */
    private Mono<DirectoryNode> getDirectoryTree(String directoryId) {
        return webClient.get()
                .uri(properties.directoryTreePath(), directoryId)
                .retrieve()
                .bodyToMono(DirectoryNode.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .doOnError(e -> LOGGER.log(Level.SEVERE, "Error obteniendo arbol para directoryId={0}: {1}",
                        new Object[]{directoryId, e.getMessage()}));
    }

    /**
     * Flattens and filters a directory tree structure into a list.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Crea una lista vacía para almacenar el resultado.</li>
     * <li>Inicia el recorrido recursivo llamando a {@code traverse}.</li>
     * <li>Retorna la lista plana.</li>
     * </ol>
     * </p>
     *
     * @param root the root node
     * @return a list of filtered nodes
     */
    private List<DirectoryNode> flattenAndFilter(DirectoryNode root) {
        List<DirectoryNode> result = new ArrayList<>();
        traverse(root, result);
        return result;
    }

    /**
     * Recursively traverses nodes, collecting valid document nodes.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Retorna de inmediato si el nodo actual es nulo.</li>
     * <li>Verifica si el nodo tiene una fuente válida y contiene identificadores de negocio requeridos.</li>
     * <li>Agrega el nodo a la lista de resultado si pasa las validaciones.</li>
     * <li>Llama recursivamente a este método para todos sus hijos, si tiene.</li>
     * </ol>
     * </p>
     *
     * @param node the current node
     * @param result the list to collect valid nodes into
     */
    private void traverse(DirectoryNode node, List<DirectoryNode> result) {
        if (node == null) return;
        if (node.getSource() != null && VALID_SOURCES.contains(node.getSource())
                && node.getBusinessDocumentId() != null && node.getProductId() != null) {
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
