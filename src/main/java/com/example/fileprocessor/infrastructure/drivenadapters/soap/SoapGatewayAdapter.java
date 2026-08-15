package com.example.fileprocessor.infrastructure.drivenadapters.soap;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILED;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.GATEWAY_TIMEOUT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.INVALID_RESPONSE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.UNKNOWN_ERROR;

import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.drivenadapters.AdapterErrorMapper;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.mapper.SoapMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Unified and streamlined SOAP gateway adapter.
 * Fixed to unwrap RetryExhaustedException and capture the underlying SOAP
 * Fault.
 */
@Component
public class SoapGatewayAdapter implements SoapGateway {

    private static final Logger LOGGER = Logger.getLogger(SoapGatewayAdapter.class.getName());

    private final WebClient soapWebClient;
    private final SoapProperties properties;
    private final SoapMapper mapper;

    /**
     * Constructs a new {@link SoapGatewayAdapter}.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Inicializa el {@link WebClient} con la URL base proporcionada en las propiedades.</li>
     * <li>Asigna las propiedades de configuración y el mapeador SOAP.</li>
     * </ol>
     * </p>
     *
     * @param webClientBuilder the WebClient builder
     * @param properties the SOAP properties
     * @param mapper the SOAP mapper
     */
    public SoapGatewayAdapter(WebClient.Builder webClientBuilder, SoapProperties properties, SoapMapper mapper) {
        this.soapWebClient = webClientBuilder
                .baseUrl(properties.endpoint())
                .build();
        this.properties = properties;
        this.mapper = mapper;
    }

    /**
     * Sends a file upload request via SOAP.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Obtiene el traceId del contexto reactivo o genera uno nuevo si no existe.</li>
     * <li>Registra el inicio de la operación de envío.</li>
     * <li>Delega el envío al método {@code sendWithRetry} iniciando en el intento 1.</li>
     * </ol>
     * </p>
     *
     * @param request the file upload request
     * @return a Flux emitting the response
     */
    @Override
    public Flux<FileUploadResponse> send(FileUploadRequest request) {
        return Flux.deferContextual(ctx -> {
            final String messageId = ctx.getOrDefault(ApiConstants.HEADER_TRACE_ID, "unknown");
            final String traceId = UUID.randomUUID().toString();
            LOGGER.log(Level.INFO, "message id: {0} : {1}",new Object[]{messageId, request});
            return sendWithRetry(request, traceId, 1);
        });
    }

    /**
     * Sends the SOAP request with retry logic.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Construye el sobre SOAP usando el mapeador.</li>
     * <li>Envía la petición POST al endpoint SOAP.</li>
     * <li>Espera la respuesta XML y la procesa en un objeto de dominio.</li>
     * <li>Si ocurre un error, maneja el fallo a través de {@code handleFinalError}.</li>
     * <li>Si la respuesta indica un fallo transitorio y se tienen intentos restantes, reintenta con un retraso.</li>
     * </ol>
     * </p>
     *
     * @param request the original request
     * @param traceId the trace identifier
     * @param attempt the current retry attempt
     * @return a Flux of file upload responses
     */
    private Flux<FileUploadResponse> sendWithRetry(FileUploadRequest request, String traceId, int attempt) {
        return mapper.buildEnvelope(request, traceId)
                .flatMapMany(envelope -> soapWebClient.post()
                        .contentType(MediaType.TEXT_XML)
                        .header("SOAPAction", properties.soapAction() != null ? properties.soapAction() : "")
                        .bodyValue(envelope)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .switchIfEmpty(Mono.error(new com.example.fileprocessor.domain.exception.ProcessingException(
                        INVALID_RESPONSE.value(),
                        INVALID_RESPONSE.name(), traceId)))
                .map(xml -> mapper.parseResponse(xml, traceId).toBuilder()
                        .traceId(traceId)
                        .attemptCount(attempt)
                        .build())
                .onErrorResume(error -> handleFinalError(error, traceId)
                        .map(errorResp -> errorResp.toBuilder().attemptCount(attempt).build()))
                .flatMapMany(response -> {
                    boolean isRetryable = !response.isSuccess() &&
                                         ProcessingResultCodes.isTransient(response.getSyncStatus()) &&
                                         attempt <= properties.retryAttempts();

                    if (isRetryable) {
                        LOGGER.log(Level.INFO, "[TraceID: {0}] Technical retry {1}/{2} due to: {3}",
                                new Object[]{traceId, attempt, properties.retryAttempts(), response.getMessage()});

                        return Flux.just(response.toBuilder().technicalRetry(true).build())
                                .concatWith(Mono.delay(Duration.ofMillis(500))
                                        .flatMapMany(unused -> sendWithRetry(request, traceId, attempt + 1)));
                    }
                    return Flux.just(response.toBuilder().technicalRetry(false).build());
                })
        );
    }


    /**
     * Handles final errors thrown during the SOAP request execution.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Si es un error HTTP, intenta parsear el cuerpo XML para extraer un posible SOAP Fault.</li>
     * <li>Resuelve el código de error de dominio delegando en {@link AdapterErrorMapper}.</li>
     * <li>Navega por la causa raíz para extraer el mensaje de error más específico.</li>
     * <li>Construye y devuelve un objeto de respuesta fallida con el estado y mensaje apropiados.</li>
     * </ol>
     * </p>
     *
     * @param error the exception thrown
     * @param traceId the trace identifier
     * @return a Mono emitting a failure response
     */
    private Mono<FileUploadResponse> handleFinalError(Throwable error, String traceId) {
        if (error instanceof WebClientResponseException wce) {
            String rawBody = wce.getResponseBodyAsString();
            if (isXml(rawBody)) {
                try {
                    return Mono.just(mapper.parseResponse(rawBody, traceId));
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Failed to parse Fault from error body", e);
                }
            }
        }

        // Delegate all HTTP/timeout/connection error mapping to the shared infrastructure utility
        String syncStatus = AdapterErrorMapper.resolveErrorCode(error);

        // Unwrap to find the root cause (e.g. SSLHandshakeException, ConnectException) for accurate messages
        Throwable root = error;
        while (root.getCause() != null && root != root.getCause()) {
            if (root instanceof WebClientResponseException) {
                break;
            }
            root = root.getCause();
        }

        String message = root.getMessage();

        if (root instanceof WebClientResponseException wce) {
            message = String.format("HTTP %d - %s", wce.getStatusCode().value(), wce.getStatusText());
        } else if (syncStatus.equals(GATEWAY_TIMEOUT.name())) {
            message = "Timeout: El servicio no respondió en " + properties.timeoutSeconds() + " segundos";
        } else if (root instanceof java.net.ConnectException) {
            message = "Connection refused: El servicio no está disponible";
        }

        if (message == null || message.isBlank()) {
            message = error.getMessage();
        }

        return Mono.just(FileUploadResponse.builder()
                .status(FAILED.name())
                .message(message != null && !message.isBlank() ? message : UNKNOWN_ERROR.value())
                .syncStatus(syncStatus)
                .traceId(traceId)
                .processedAt(Instant.now())
                .success(false)
                .build());
    }

    /**
     * Checks whether a given string is considered XML.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Verifica si el texto no es nulo y comienza con el carácter de etiqueta '&lt;'.</li>
     * <li>Asegura que el contenido no contenga una etiqueta de HTML, evitando que se detecten páginas de error HTML.</li>
     * <li>Devuelve el resultado de la evaluación.</li>
     * </ol>
     * </p>
     *
     * @param body the string to check
     * @return true if XML, false otherwise
     */
    private boolean isXml(String body) {
        return body != null && body.trim().startsWith("<") && !body.toLowerCase().contains("<html");
    }
}
