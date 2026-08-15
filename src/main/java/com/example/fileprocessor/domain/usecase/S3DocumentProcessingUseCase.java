package com.example.fileprocessor.domain.usecase;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILURE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SUCCESS;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.product.ProcessingContext;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.S3Gateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.logging.Level;

/**
 * Use case for processing documents via S3 using generic AbstractDocumentProcessingUseCase.
 */
public class S3DocumentProcessingUseCase extends AbstractDocumentProcessingUseCase<Document, DocumentHistoryDTO> {

    private final DocumentPersistenceGateway persistencePort;
    private final ProductRestGateway productRestGateway;
    private final S3Gateway s3Gateway;

    /**
     * Constructor para inicializar las dependencias del caso de uso.
     * <p>
     * Secuencia:
     * 1. Llama al constructor de la clase base.
     * 2. Asigna el gateway de persistencia.
     * 3. Asigna el gateway de producto.
     * 4. Asigna el gateway de S3.
     */
    public S3DocumentProcessingUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            S3Gateway s3Gateway,
            RulesBussinesGateway<DocumentHistoryDTO> documentValidator,
            String tempDirPath) {
        super(persistencePort, documentValidator, tempDirPath);
        this.persistencePort = persistencePort;
        this.productRestGateway = productRestGateway;
        this.s3Gateway = s3Gateway;
    }

    /**
     * Obtiene los documentos pendientes para ser procesados desde la base de datos.
     * <p>
     * Secuencia:
     * 1. Llama al gateway de persistencia para buscar documentos pendientes del día actual.
     * 2. Filtra los resultados usando el nombre de la implementación actual.
     */
    @Override
    protected Flux<Document> getPendingDocuments(LocalDateTime startOfDay) {
        return persistencePort.findPendingDocumentsToday(implementationName(), startOfDay);
    }

    /**
     * Construye el historial inicial a partir del documento original.
     * <p>
     * Secuencia:
     * 1. Convierte la entidad Document en un DocumentHistoryDTO usando el método estático de la clase DTO.
     */
    @Override
    protected DocumentHistoryDTO buildInitialHistory(Document doc) {
        return DocumentHistoryDTO.fromDocument(doc);
    }

    /**
     * Descarga el contenido del documento desde el servicio REST.
     * <p>
     * Secuencia:
     * 1. Consume el servicio REST enviando el productId y el businessDocumentId.
     * 2. Mapea la respuesta del archivo.
     * 3. Crea una copia actualizada del historial base con los metadatos obtenidos (tamaño, tipo de contenido, etc.).
     * 4. Retorna un nuevo ProcessingContext con el historial actualizado y el contenido binario.
     */
    @Override
    protected Mono<ProcessingContext<DocumentHistoryDTO>> downloadDocumentContent(DocumentHistoryDTO baseHistory) {
        return productRestGateway.getDocument(baseHistory.getProductId(), baseHistory.getBusinessDocumentId())
                .map(file -> {
                    DocumentHistoryDTO updatedHistory = baseHistory.toBuilder()
                            .size(file.getSize())
                            .contentType(file.getContentType())
                            .filename(file.getFilename())
                            .originFolder(file.getOriginFolder())
                            .originCountry(file.getOriginCountry())
                            .isZip(file.getIsZip())
                            .build();
                    return new ProcessingContext<>(updatedHistory, file.getContent());
                });
    }

    /**
     * Construye un historial para una entrada descomprimida proveniente de un archivo ZIP.
     * <p>
     * Secuencia:
     * 1. Toma el historial del archivo ZIP original.
     * 2. Genera una copia modificando el businessDocumentId para anexar el nombre de la entrada.
     * 3. Actualiza el nombre de archivo, determina el tipo MIME usando la utilidad correspondiente y marca que ya no es un ZIP.
     */
    @Override
    protected DocumentHistoryDTO buildDecompressedEntryHistory(DocumentHistoryDTO zipHistory, String entryName) {
        return zipHistory.toBuilder()
                .businessDocumentId(zipHistory.getBusinessDocumentId() + "/" + entryName)
                .filename(entryName)
                .contentType(com.example.fileprocessor.domain.util.MimeTypeUtil.getMimeType(entryName))
                .isZip(false)
                .build();
    }

    /**
     * Sube el documento al bucket S3 usando el gateway correspondiente.
     * <p>
     * Secuencia:
     * 1. Construye el request de carga a partir del historial y el contenido.
     * 2. Envía el requerimiento al gateway de S3.
     * 3. Si la respuesta es exitosa, mapea y devuelve un FileUploadResponse con el estado SUCCESS y datos de correlación.
     * 4. En caso de error, captura la excepción, registra el error y retorna un FileUploadResponse con estado FAILURE.
     */
    @Override
    protected Flux<FileUploadResponse> uploadDocument(ProcessingContext<DocumentHistoryDTO> context, Long docId) {
        DocumentHistoryDTO history = context.getHistory();
        return Mono.fromCallable(() -> FileUploadRequest.from(history, context.getFileContent(), docId, null))
            .flatMap(request -> s3Gateway.send(request))
            .flux()
            .map(response -> {
                if (response.isSuccess()) {
                    return FileUploadResponse.builder()
                        .success(true)
                        .status(SUCCESS.name())
                        .message(SUCCESS.value())
                        .processedAt(Instant.now())
                        .correlationId(response.getCorrelationId())
                        .externalReference(response.getExternalReference())
                        .build();
                }
                return response;
            })
            .onErrorResume(e -> {
                LOGGER.log(Level.SEVERE, "S3 processing failed for docId {0}: {1}", new Object[]{docId, e.getMessage()});
                return Mono.just(FileUploadResponse.builder()
                    .status(FAILURE.name())
                    .success(false)
                    .message(e.getMessage())
                    .processedAt(Instant.now())
                    .build());
            });
    }

    /**
     * Retorna el nombre de la implementación.
     * <p>
     * Secuencia:
     * 1. Devuelve la constante o cadena de texto "S3" que identifica a este caso de uso.
     */
    @Override
    protected String implementationName() {
        return "S3";
    }
}
