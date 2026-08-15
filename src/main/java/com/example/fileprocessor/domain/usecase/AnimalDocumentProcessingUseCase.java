package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.animal.AnimalDocument;
import com.example.fileprocessor.domain.entity.product.ProcessingContext;
import com.example.fileprocessor.domain.entity.animal.AnimalDocumentHistoryDTO;
import com.example.fileprocessor.domain.port.out.PersistenceGateway;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Caso de uso específico para las reglas de negocio de carga (upload) de Animales.
 * Extiende del UseCase base genérico.
 */
public class AnimalDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase<AnimalDocument, AnimalDocumentHistoryDTO> {

    private final AnimalDocumentProvider animalDocumentProvider;
    private final ProductRestGateway productRestGateway;
    private final SoapGateway soapGateway;
    private final HomologationRepository homologationRepository;

    /**
     * Constructor para inicializar las dependencias del caso de uso.
     * <p>
     * Secuencia:
     * 1. Llama al constructor de la clase base.
     * 2. Asigna el proveedor de documentos de animales.
     * 3. Asigna el gateway REST de producto.
     * 4. Asigna el gateway SOAP.
     * 5. Asigna el repositorio de homologación.
     */
    public AnimalDocumentProcessingUseCase(
            PersistenceGateway<AnimalDocument, AnimalDocumentHistoryDTO> persistencePort,
            ProductRestGateway productRestGateway,
            RulesBussinesGateway<AnimalDocumentHistoryDTO> documentValidator,
            String tempDirPath,
            AnimalDocumentProvider animalDocumentProvider,
            SoapGateway soapGateway,
            HomologationRepository homologationRepository) {
        super(persistencePort, documentValidator, tempDirPath);
        this.animalDocumentProvider = animalDocumentProvider;
        this.productRestGateway = productRestGateway;
        this.soapGateway = soapGateway;
        this.homologationRepository = homologationRepository;
    }

    /**
     * Obtiene los documentos pendientes para ser procesados. En este caso retorna un Flux vacío, ya que se rige por un flujo dinámico.
     * <p>
     * Secuencia:
     * 1. Retorna un Flux vacío, indicando que no se buscan documentos pendientes de forma tradicional.
     */
    @Override
    protected Flux<AnimalDocument> getPendingDocuments(LocalDateTime startOfDay) {
        return Flux.empty(); // Fiel al flujo dinámico REST de animales
    }

    /**
     * Construye el historial inicial a partir del documento del animal.
     * <p>
     * Secuencia:
     * 1. Extrae los datos básicos del AnimalDocument.
     * 2. Asigna los atributos específicos como animalId, raza y tipo.
     * 3. Retorna un nuevo objeto AnimalDocumentHistoryDTO construido con esos valores.
     */
    @Override
    protected AnimalDocumentHistoryDTO buildInitialHistory(AnimalDocument doc) {
        return AnimalDocumentHistoryDTO.builder()
                .documentId(doc.getId())
                .businessDocumentId(doc.getDocumentId())
                .state(doc.getState())
                .useCase(doc.getUseCase())
                .retryCount(doc.getRetryCountSafe())
                .filename(doc.getName())
                .startedAt(java.time.Instant.now())
                .animalId(doc.getAnimalId()) // Mapeo directo
                .raza(doc.getRaza())
                .tipo(doc.getTipo())
                .isZip(doc.getIsZip())
                .build();
    }

    /**
     * Descarga el contenido del documento del animal desde el servicio REST.
     * <p>
     * Secuencia:
     * 1. Solicita el documento al gateway REST usando productId y businessDocumentId.
     * 2. Actualiza el historial base con el tamaño, tipo, nombre de archivo y otros metadatos recibidos.
     * 3. Retorna el ProcessingContext con el historial y el arreglo de bytes del contenido.
     */
    @Override
    protected Mono<ProcessingContext<AnimalDocumentHistoryDTO>> downloadDocumentContent(AnimalDocumentHistoryDTO baseHistory) {
        return productRestGateway.getDocument(baseHistory.getProductId(), baseHistory.getBusinessDocumentId())
                .map(file -> {
                    AnimalDocumentHistoryDTO updatedHistory = baseHistory.toBuilder()
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
     * Construye el historial para una entrada extraída de un archivo ZIP.
     * <p>
     * Secuencia:
     * 1. Modifica el identificador del documento adjuntando el nombre de la entrada.
     * 2. Asigna el nombre, determina el Content-Type adecuado y establece que no es un archivo comprimido.
     */
    @Override
    protected AnimalDocumentHistoryDTO buildDecompressedEntryHistory(AnimalDocumentHistoryDTO zipHistory, String entryName) {
        return zipHistory.toBuilder()
                .businessDocumentId(zipHistory.getBusinessDocumentId() + "/" + entryName)
                .filename(entryName)
                .contentType(com.example.fileprocessor.domain.util.MimeTypeUtil.getMimeType(entryName))
                .isZip(false)
                .build();
    }

    /**
     * Sube el documento del animal enviándolo a través del gateway SOAP previa homologación.
     * <p>
     * Secuencia:
     * 1. Consulta el repositorio de homologación para obtener los valores equivalentes de categoría, país y carpeta.
     * 2. Si hay valores de homologación para el país, actualiza el historial.
     * 3. Actualiza el historial con la categoría homologada.
     * 4. Crea la petición de carga (FileUploadRequest) específica para animales.
     * 5. Envía la solicitud al servicio SOAP.
     */
    @Override
    protected Flux<FileUploadResponse> uploadDocument(ProcessingContext<AnimalDocumentHistoryDTO> context, Long docId) {
        AnimalDocumentHistoryDTO history = context.getHistory();
        return homologationRepository.resolve(history)
                .flatMapMany(homologation -> {
                    if (homologation.homologationCountry() != null) {
                        history.setHomologationFolder(homologation.homologationCountry().homologationFolder());
                        history.setHomologationCountry(homologation.homologationCountry().homologationCountry());
                    }
                    history.setCategoriaHomologada(homologation.categoriaDocument());

                    FileUploadRequest uploadReq = FileUploadRequest.fromAnimal(
                            history, context.getFileContent(), docId, homologation);
                    return soapGateway.send(uploadReq);
                });
    }

    /**
     * Retorna el nombre de la implementación.
     * <p>
     * Secuencia:
     * 1. Retorna la constante USE_CASE_NAME definida en la entidad AnimalDocument.
     */
    @Override
    protected String implementationName() {
        return AnimalDocument.USE_CASE_NAME;
    }

    /**
     * Ejecuta el procesamiento de los documentos pendientes consultando el proveedor externo.
     * <p>
     * Secuencia:
     * 1. Registra un mensaje en el log indicando el inicio del proceso.
     * 2. Obtiene los documentos pendientes desde el AnimalDocumentProvider.
     * 3. Para cada documento, genera un traceId y procesa el documento con trazabilidad.
     */
    public Flux<FileUploadResponse> executePendingDocuments() {
        LOGGER.info("Iniciando procesamiento diario Animal...");
        return animalDocumentProvider.getAllPendingAnimalDocuments()
                .concatMap(doc -> {
                    var traceId = "Animal-" + doc.getAnimalId() + "-" + doc.getDocumentId();
                    return processWithTracking(doc, traceId);
                });
    }
}
