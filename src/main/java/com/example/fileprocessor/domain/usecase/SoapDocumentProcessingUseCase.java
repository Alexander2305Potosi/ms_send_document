package com.example.fileprocessor.domain.usecase;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.FAILURE;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.UNKNOWN_ERROR;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.product.ProcessingContext;
import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.logging.Level;

/**
 * Use case for processing documents via SOAP using generic AbstractDocumentProcessingUseCase.
 */
public class SoapDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase<Document, DocumentHistoryDTO> {

    private final DocumentPersistenceGateway persistencePort;
    private final ProductRestGateway productRestGateway;
    private final SoapGateway soapGateway;
    private final HomologationRepository homologationRepository;

    /**
     * Constructor para inicializar las dependencias del caso de uso.
     * <p>
     * Secuencia:
     * 1. Llama al constructor de la clase base.
     * 2. Asigna el gateway de persistencia.
     * 3. Asigna el gateway REST del producto.
     * 4. Asigna el gateway SOAP.
     * 5. Asigna el repositorio de homologación.
     */
    public SoapDocumentProcessingUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            SoapGateway soapGateway,
            RulesBussinesGateway<DocumentHistoryDTO> documentValidator,
            HomologationRepository homologationRepository,
            String tempDirPath) {
        super(persistencePort, documentValidator, tempDirPath);
        this.persistencePort = persistencePort;
        this.productRestGateway = productRestGateway;
        this.soapGateway = soapGateway;
        this.homologationRepository = homologationRepository;
    }

    /**
     * Obtiene los documentos pendientes para procesar.
     * <p>
     * Secuencia:
     * 1. Consulta el persistencePort buscando documentos pendientes del día actual.
     * 2. Utiliza el nombre de implementación para el filtro en base de datos.
     */
    @Override
    protected Flux<Document> getPendingDocuments(LocalDateTime startOfDay) {
        return persistencePort.findPendingDocumentsToday(implementationName(), startOfDay);
    }

    /**
     * Construye el historial inicial a partir del documento.
     * <p>
     * Secuencia:
     * 1. Convierte el documento proporcionado a un DocumentHistoryDTO inicial.
     */
    @Override
    protected DocumentHistoryDTO buildInitialHistory(Document doc) {
        return DocumentHistoryDTO.fromDocument(doc);
    }

    /**
     * Descarga el contenido del documento desde el servicio REST.
     * <p>
     * Secuencia:
     * 1. Consume el servicio REST con el productId y el businessDocumentId.
     * 2. Mapea la respuesta, enriqueciendo el historial base con los metadatos descargados.
     * 3. Retorna un ProcessingContext con el historial actualizado y el array de bytes descargado.
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
     * Construye un historial para un archivo extraído de un ZIP.
     * <p>
     * Secuencia:
     * 1. A partir del historial del ZIP, crea uno nuevo.
     * 2. Cambia el businessDocumentId agregando el nombre de entrada.
     * 3. Obtiene y asigna el MimeType de la entrada y marca la bandera isZip en false.
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
     * Sube el documento por medio de SOAP.
     * <p>
     * Secuencia:
     * 1. Resuelve la homologación correspondiente para el documento.
     * 2. Si hay datos de país, actualiza carpeta y país en el historial.
     * 3. Actualiza la categoría homologada.
     * 4. Crea el requerimiento a partir del historial y el contenido, y lo envía al gateway SOAP.
     * 5. Mapea la respuesta agregando detalles de la homologación.
     * 6. En caso de error, captura la excepción y retorna un objeto de respuesta con estado de FALLO.
     */
    @Override
    protected Flux<FileUploadResponse> uploadDocument(ProcessingContext<DocumentHistoryDTO> context, Long docId) {
        DocumentHistoryDTO history = context.getHistory();
        return homologationRepository.resolve(history)
                .flatMapMany(h -> {
                    if (h.homologationCountry() != null) {
                        history.setHomologationFolder(h.homologationCountry().homologationFolder());
                        history.setHomologationCountry(h.homologationCountry().homologationCountry());
                    }
                    history.setCategoriaHomologada(h.categoriaDocument());

                    FileUploadRequest request = FileUploadRequest.from(history, context.getFileContent(), docId, h);
                    return soapGateway.send(request)
                            .map(resp -> resp.toBuilder()
                                    .homologationFolder(h.homologationCountry() != null ? h.homologationCountry().homologationFolder() : null)
                                    .homologationCountry(h.homologationCountry() != null ? h.homologationCountry().homologationCountry() : null)
                                    .categoriaHomologada(h.categoriaDocument())
                                    .build());
                })
                .onErrorResume(e -> {
                    LOGGER.log(Level.SEVERE, "SOAP fatal failure for docId {0}: {1}",
                            new Object[] { docId, e.getMessage() });

                    return Flux.just(FileUploadResponse.builder()
                            .status(FAILURE.name())
                            .syncStatus(UNKNOWN_ERROR.name())
                            .message(e.getMessage())
                            .success(false)
                            .filename(history.getFilename())
                            .processedAt(Instant.now())
                            .build());
                });
    }

    /**
     * Retorna el nombre de la implementación.
     * <p>
     * Secuencia:
     * 1. Retorna la constante o cadena de texto "SOAP".
     */
    @Override
    protected String implementationName() {
        return "SOAP";
    }
}
