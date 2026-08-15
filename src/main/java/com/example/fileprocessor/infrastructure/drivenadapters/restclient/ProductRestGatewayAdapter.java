package com.example.fileprocessor.infrastructure.drivenadapters.restclient;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.INVALID_BASE64;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.UNKNOWN_ERROR;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.maestro.ProductDocumentFile;
import com.example.fileprocessor.domain.entity.product.maestro.ProductMaestro;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.util.Base64Utils;
import com.example.fileprocessor.infrastructure.drivenadapters.AdapterErrorMapper;
import com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto.ProductDocumentResponse;
import com.example.fileprocessor.infrastructure.entrypoints.rest.config.DocumentRestProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class ProductRestGatewayAdapter implements ProductRestGateway {

    private static final Logger LOGGER = Logger.getLogger(ProductRestGatewayAdapter.class.getName());

    private final WebClient webClient;
    private final DocumentRestProperties properties;

    /**
     * Constructs a new ProductRestGatewayAdapter.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Configura un HttpClient con el tiempo de espera definido.</li>
     * <li>Construye el WebClient usando el builder inyectado, estableciendo la URL base, conectores y límites en memoria.</li>
     * <li>Almacena la referencia a las propiedades y el cliente creado.</li>
     * </ol>
     * </p>
     *
     * @param webClientBuilder the builder
     * @param properties the document REST properties
     */
    public ProductRestGatewayAdapter(WebClient.Builder webClientBuilder,
            DocumentRestProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        this.webClient = webClientBuilder
                .baseUrl(properties.endpoint())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(25 * 1024 * 1024))
                .build();
    }

    /**
     * Retrieves a list of documents for a given product.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Obtiene el trace ID del contexto.</li>
     * <li>Realiza una solicitud GET hacia el API de documentos usando el ID del producto.</li>
     * <li>Mapea la respuesta JSON hacia un flujo de objetos de la clase de dominio {@link Document}.</li>
     * <li>Maneja los errores convirtiéndolos en {@link ProcessingException}.</li>
     * </ol>
     * </p>
     *
     * @param product the product maestro
     * @return a Flux of documents
     */
    @Override
    public Flux<Document> getDocumentsByProduct(ProductMaestro product) {
        return Flux.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault(ApiConstants.HEADER_TRACE_ID, "unknown-trace");
            LOGGER.log(Level.INFO, "Fetching documents for product {0} from REST API, traceId: {1}",
                    new Object[] { product.getProductId(), traceId });

            return webClient.get()
                    .uri(properties.productDocumentsPath(), product.getProductId())
                    .accept(MediaType.APPLICATION_JSON)
                    .header(ApiConstants.HEADER_TRACE_ID, traceId)
                    .retrieve()
                    .bodyToFlux(ProductDocumentResponse.class)
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .map(doc -> mapToDocument(product.getProductId(), doc))
                    .doOnNext(doc -> LOGGER.log(Level.INFO, "Document retrieved: productId={0}, documentId={1}",
                            new Object[] { doc.getProductId(), doc.getDocumentId() }))
                    .onErrorMap(error -> {
                        LOGGER.log(Level.WARNING, "[REST] Error fetching documents for product {0}: {1}",
                                new Object[] { product.getProductId(), error.getMessage() });
                        return mapToProcessingException(error, traceId);
                    });
        });
    }

    /**
     * Retrieves the file content and metadata for a specific document of a product.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Recupera el trace ID desde el contexto.</li>
     * <li>Ejecuta un request GET al endpoint usando el ID del producto y el ID del documento.</li>
     * <li>Parsea la respuesta JSON al tipo {@link ProductDocumentResponse}.</li>
     * <li>Mapea el DTO devuelto a una entidad de dominio {@link ProductDocumentFile}, incluyendo la decodificación de Base64.</li>
     * <li>Captura errores de red y los mapea a dominios de error correspondientes.</li>
     * </ol>
     * </p>
     *
     * @param productId the product ID
     * @param documentId the document ID
     * @return a Mono emitting the document file data
     */
    @Override
    public Mono<ProductDocumentFile> getDocument(String productId, String documentId) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault(ApiConstants.HEADER_TRACE_ID, "unknown-trace");
            LOGGER.log(Level.INFO, "Fetching document {0} for product {1} from REST API, traceId: {2}",
                    new Object[] { documentId, productId, traceId });

            return webClient.get()
                    .uri(properties.productDocumentsPath() + "/{documentId}", productId, documentId)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(ApiConstants.HEADER_TRACE_ID, traceId)
                    .retrieve()
                    .bodyToMono(ProductDocumentResponse.class)
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .map(response -> mapToProductDocumentFile(productId, response))
                    .doOnNext(doc -> LOGGER.log(Level.INFO, "Document {0} retrieved for product {1}",
                            new Object[] { documentId, productId }))
                    .onErrorMap(error -> {
                        LOGGER.log(Level.WARNING, "[REST] Error fetching document {0} for product {1}: {2}",
                                new Object[] { documentId, productId, error.getMessage() });
                        return mapToProcessingException(error, traceId);
                    });
        });
    }

    /**
     * Maps a DTO to a {@link ProductDocumentFile}.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Llama a la función que decodifica el string base64 a arreglo de bytes.</li>
     * <li>Calcula o asigna el tamaño real del contenido.</li>
     * <li>Construye un nuevo objeto {@link ProductDocumentFile} copiando los atributos relevantes.</li>
     * <li>Establece la propiedad isZip verificando la extensión del nombre de archivo.</li>
     * </ol>
     * </p>
     *
     * @param productId the product ID
     * @param json the JSON response DTO
     * @return the mapped file entity
     */
    private ProductDocumentFile mapToProductDocumentFile(String productId, ProductDocumentResponse json) {
        byte[] content = decodeBase64(json);
        long size = json.getSize() != null ? json.getSize() : (content != null ? content.length : 0);

        return ProductDocumentFile.builder()
                .productId(productId)
                .documentId(json.getDocumentId())
                .filename(json.getFilename())
                .content(content)
                .contentType(json.getContentType())
                .size(size)
                .isZip(json.isZip() || (json.getFilename() != null && json.getFilename().toLowerCase().endsWith(".zip")))
                .originFolder(json.getOriginFolder())
                .originCountry(json.getOriginCountry())
                .build();
    }

    /**
     * Maps a DTO to a basic {@link Document}.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Construye un objeto {@link Document} usando el patrón Builder.</li>
     * <li>Asigna el ID de producto y el ID de documento desde los parámetros.</li>
     * <li>Determina si es ZIP en base a las propiedades o nombre de archivo.</li>
     * <li>Devuelve la entidad construida.</li>
     * </ol>
     * </p>
     *
     * @param productId the product ID
     * @param json the response payload
     * @return the mapped basic document
     */
    private Document mapToDocument(String productId, ProductDocumentResponse json) {
        return Document.builder()
                .productId(productId)
                .documentId(json.getDocumentId())
                .name(json.getFilename())
                .isZip(json.isZip() || (json.getFilename() != null && json.getFilename().toLowerCase().endsWith(".zip")))
                .build();
    }

    /**
     * Decodes the Base64 content of a document payload.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Comprueba si el string codificado en base64 es nulo o está vacío, devolviendo nulo en ese caso.</li>
     * <li>Intenta decodificar el string base64 utilizando utilidades seguras.</li>
     * <li>Si falla la decodificación, lanza una excepción de dominio {@link ProcessingException} con el código de error correspondiente.</li>
     * </ol>
     * </p>
     *
     * @param json the DTO containing the content
     * @return the decoded byte array, or null if empty
     */
    private byte[] decodeBase64(ProductDocumentResponse json) {
        String contentBase64 = json.getContent();
        if (contentBase64 == null || contentBase64.isBlank())
            return null;

        try {
            return Base64Utils.decodeSafe(contentBase64, json.getFilename(), json.getDocumentId());
        } catch (Exception e) {
            throw new ProcessingException(
                    "Base64 decode failed for document: " + json.getDocumentId(),
                    INVALID_BASE64.name(), json.getDocumentId());
        }
    }

    /**
     * Translates any network/HTTP error into a {@link ProcessingException} with the
     * appropriate domain error code, delegating the mapping logic to {@link AdapterErrorMapper}.
     * Already-mapped {@link ProcessingException} instances are returned as-is.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Si el error ya es una {@link ProcessingException}, la retorna directamente.</li>
     * <li>Resuelve el código de error correspondiente llamando a {@link AdapterErrorMapper#resolveErrorCode}.</li>
     * <li>Crea y devuelve una nueva instancia de {@link ProcessingException} combinando el mensaje original y el código determinado.</li>
     * </ol>
     * </p>
     *
     * @param error the root error
     * @param traceId the trace ID
     * @return the mapped exception
     */
    private static ProcessingException mapToProcessingException(Throwable error, String traceId) {
        if (error instanceof ProcessingException pe) {
            return pe;
        }
        String code = AdapterErrorMapper.resolveErrorCode(error);
        return new ProcessingException(
                error.getMessage() != null ? error.getMessage() : UNKNOWN_ERROR.value(),
                code,
                traceId);
    }
}
