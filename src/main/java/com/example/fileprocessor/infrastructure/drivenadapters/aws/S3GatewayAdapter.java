package com.example.fileprocessor.infrastructure.drivenadapters.aws;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.DEST_UNAUTHORIZED;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.EMPTY_CONTENT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILURE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.GATEWAY_TIMEOUT;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SERVICE_UNAVAILABLE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SOURCE_NOT_FOUND;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SUCCESS;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.UNKNOWN_ERROR;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import com.example.fileprocessor.infrastructure.drivenadapters.aws.config.S3Properties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.constants.ApiConstants;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

@org.springframework.context.annotation.Profile("s3")
@Component
public class S3GatewayAdapter implements S3Gateway {

    private static final Logger LOGGER = Logger.getLogger(S3GatewayAdapter.class.getName());

    private final S3AsyncClient s3Client;
    private final S3Properties s3Properties;

    /**
     * Constructor for S3GatewayAdapter.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Asigna el cliente asíncrono de S3.</li>
     * <li>Asigna las propiedades de configuración de S3.</li>
     * </ol>
     * </p>
     *
     * @param s3Client the async S3 client
     * @param s3Properties the S3 configuration properties
     */
    public S3GatewayAdapter(S3AsyncClient s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    /**
     * Sends a file to S3 storage.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Extrae el ID de traza del contexto reactivo.</li>
     * <li>Valida que el contenido del archivo no esté vacío. Si lo está, devuelve un error.</li>
     * <li>Construye la clave (key) para el archivo en S3.</li>
     * <li>Configura y ejecuta la petición {@link PutObjectRequest} de manera asíncrona.</li>
     * <li>Aplica reintentos si ocurre un error transitorio.</li>
     * <li>Mapea el resultado exitoso o maneja los errores mediante {@code handleS3Error}.</li>
     * </ol>
     * </p>
     *
     * @param request the file upload request
     * @return a Mono emitting the response
     */
    @Override
    public Mono<FileUploadResponse> send(FileUploadRequest request) {
        return Mono.deferContextual(ctx -> {
            String traceId = ctx.getOrDefault(ApiConstants.HEADER_TRACE_ID, "unknown");
            LOGGER.log(Level.INFO, "Sending S3 upload request for documentId: {0}, traceId: {1}", new Object[]{request.getDocumentId(), traceId});

            byte[] content = request.getContent();
            if (content == null || content.length == 0) {
                LOGGER.log(Level.WARNING, "S3 upload skipped for documentId={0} - content is null or empty", new Object[]{request.getDocumentId()});
                return Mono.just(FileUploadResponse.builder()
                    .status(FAILURE.name())
                    .syncStatus(EMPTY_CONTENT.name())
                    .traceId(traceId)
                    .message(EMPTY_CONTENT.value())
                    .processedAt(Instant.now())
                    .success(false)
                    .build());
            }

            String key = buildKey(traceId, request.getFilename());

            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(s3Properties.bucketName())
                .key(key)
                .contentType(request.getContentType())
                .contentLength((long) content.length)
                .metadata(Map.of(
                    "traceId", traceId,
                    "originalFilename", request.getFilename(),
                    "documentId", request.getDocumentId()
                ))
                .build();

            return Mono.defer(() -> {
                    CompletableFuture<PutObjectResponse> future = s3Client.putObject(putRequest, AsyncRequestBody.fromBytes(content));
                    return Mono.fromFuture(future);
                })
                .timeout(Duration.ofSeconds(s3Properties.timeoutSeconds()))
                .retryWhen(Retry.backoff(s3Properties.retryAttempts(), Duration.ofMillis(s3Properties.retryBackoffMillis()))
                    .filter(this::isRetryableException)
                    .doBeforeRetry(retrySignal -> {
                        long attempt = retrySignal.totalRetries() + 1;
                        LOGGER.log(Level.WARNING, "Retrying S3 upload for documentId={0}, attempt {1}/{2}",
                            new Object[]{request.getDocumentId(), attempt, s3Properties.retryAttempts()});
                    }))
                .map(completed -> {
                    LOGGER.log(Level.INFO, "S3 upload successful: {0} -> {1}/{2}", new Object[]{request.getFilename(), s3Properties.bucketName(), key});
                    return FileUploadResponse.builder()
                        .status(SUCCESS.name())
                        .message("Uploaded to S3: " + s3Properties.bucketName() + "/" + key)
                        .correlationId(completed.eTag())
                        .traceId(traceId)
                        .processedAt(Instant.now())
                        .externalReference(key)
                        .success(true)
                        .build();
                })
                .onErrorResume(error -> handleS3Error(error, request.getDocumentId(), traceId));
        });
    }

    /**
     * Handles errors that occur during the S3 upload process.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Extrae la excepción original si el error proviene de intentos agotados.</li>
     * <li>Registra el error en los logs.</li>
     * <li>Categoriza el error usando {@code categorizeS3Error}.</li>
     * <li>Construye y devuelve un {@link FileUploadResponse} con el estado de error correspondiente.</li>
     * </ol>
     * </p>
     *
     * @param error the error thrown
     * @param documentId the document identifier
     * @param traceId the trace identifier
     * @return a Mono emitting a failure response
     */
    private Mono<FileUploadResponse> handleS3Error(Throwable error, String documentId, String traceId) {
        Throwable actualError = error;
        if (error.getCause() != null && error.getClass().getName().contains("RetryExhausted")) {
            actualError = error.getCause();
        }

        LOGGER.log(Level.SEVERE, "S3 upload failed for documentId {0}: {1}", new Object[]{documentId, actualError.getMessage()});

        String syncStatus = categorizeS3Error(actualError);
        return Mono.just(FileUploadResponse.builder()
            .status(FAILURE.name())
            .syncStatus(syncStatus)
            .traceId(traceId)
            .message(actualError.getMessage())
            .processedAt(Instant.now())
            .success(false)
            .build());
    }

    /**
     * Categorizes S3 errors into domain error codes.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Verifica si el error es de timeout para devolver {@code GATEWAY_TIMEOUT}.</li>
     * <li>Si es un {@link software.amazon.awssdk.services.s3.model.S3Exception}, evalúa el código HTTP.</li>
     * <li>Devuelve el código de dominio según el código HTTP (403, 404, 503).</li>
     * <li>Si no coincide con ninguno, devuelve {@code UNKNOWN_ERROR}.</li>
     * </ol>
     * </p>
     *
     * @param error the error to categorize
     * @return the domain error code string
     */
    String categorizeS3Error(Throwable error) {
        if (error instanceof TimeoutException) return GATEWAY_TIMEOUT.name();
        
        if (error instanceof software.amazon.awssdk.services.s3.model.S3Exception e) {
            Integer statusCode = e.statusCode();
            if (statusCode != null) {
                if (statusCode == 403) return DEST_UNAUTHORIZED.name();
                if (statusCode == 404) return SOURCE_NOT_FOUND.name();
                if (statusCode == 503) return SERVICE_UNAVAILABLE.name();
            }
        }
        return UNKNOWN_ERROR.name();
    }

    /**
     * Checks if the exception is considered retryable.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Retorna verdadero si es un {@link TimeoutException}.</li>
     * <li>Retorna verdadero si es una excepción base del SDK de AWS.</li>
     * <li>En otro caso, retorna falso.</li>
     * </ol>
     * </p>
     *
     * @param throwable the exception to check
     * @return true if retryable, false otherwise
     */
    boolean isRetryableException(Throwable throwable) {
        return throwable instanceof TimeoutException || 
               throwable instanceof software.amazon.awssdk.core.exception.SdkException;
    }

    /**
     * Builds the S3 key for the file.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Sanitiza el nombre de archivo usando {@code sanitizeFilename}.</li>
     * <li>Aplica el prefijo configurado, el ID de traza y el nombre de archivo sanitizado.</li>
     * <li>Retorna la ruta completa (key).</li>
     * </ol>
     * </p>
     *
     * @param traceId the trace identifier
     * @param filename the original file name
     * @return the formatted S3 key
     */
    String buildKey(String traceId, String filename) {
        String sanitizedFilename = sanitizeFilename(filename);
        return String.format(s3Properties.keyPrefix() + "%s/%s", traceId, sanitizedFilename);
    }

    /**
     * Sanitizes a filename to remove invalid characters.
     *
     * <p><b>Secuencia:</b>
     * <ol>
     * <li>Verifica si el nombre es nulo o vacío, en cuyo caso devuelve "unnamed".</li>
     * <li>Reemplaza cualquier carácter que no sea alfanumérico, punto, guion bajo o guion por una cadena vacía.</li>
     * <li>Devuelve el nombre sanitizado.</li>
     * </ol>
     * </p>
     *
     * @param filename the original filename
     * @return the sanitized filename
     */
    String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "unnamed";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "");
    }
}
