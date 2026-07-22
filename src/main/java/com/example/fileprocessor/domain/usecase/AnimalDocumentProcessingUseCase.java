package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.animal.AnimalDocument;
import com.example.fileprocessor.domain.entity.product.ProcessingContext;
import com.example.fileprocessor.domain.entity.animal.AnimalDocumentHistoryDTO;
import com.example.fileprocessor.domain.port.out.PersistenceGateway;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.port.out.MetadataStrategy;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.AnimalSoapGateway;
import com.example.fileprocessor.domain.port.out.AnimalRepository;
import com.example.fileprocessor.domain.port.out.AnimalRestGateway;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Caso de uso específico para las reglas de negocio de carga (upload) de Animales.
 * Extiende del UseCase base genérico.
 */
public class AnimalDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase<AnimalDocument, AnimalDocumentHistoryDTO> {

    private final AnimalRepository animalRepository;
    private final AnimalRestGateway animalRestGateway;
    private final ProductRestGateway productRestGateway;
    private final AnimalSoapGateway soapGateway;
    private final HomologationRepository homologationRepository;
    private final MetadataStrategy metadataStrategy;

    public AnimalDocumentProcessingUseCase(
            PersistenceGateway<AnimalDocument, AnimalDocumentHistoryDTO> persistencePort,
            ProductRestGateway productRestGateway,
            RulesBussinesGateway<AnimalDocumentHistoryDTO> documentValidator,
            String tempDirPath,
            AnimalRepository animalRepository,
            AnimalRestGateway animalRestGateway,
            AnimalSoapGateway soapGateway,
            HomologationRepository homologationRepository,
            MetadataStrategy metadataStrategy) {
        super(persistencePort, documentValidator, tempDirPath);
        this.animalRepository = animalRepository;
        this.animalRestGateway = animalRestGateway;
        this.productRestGateway = productRestGateway;
        this.soapGateway = soapGateway;
        this.homologationRepository = homologationRepository;
        this.metadataStrategy = metadataStrategy;
    }

    @Override
    protected Flux<AnimalDocument> getPendingDocuments(LocalDateTime startOfDay) {
        return Flux.empty(); // Fiel al flujo dinámico REST de animales
    }

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

    @Override
    protected AnimalDocumentHistoryDTO buildDecompressedEntryHistory(AnimalDocumentHistoryDTO zipHistory, String entryName) {
        return zipHistory.toBuilder()
                .businessDocumentId(zipHistory.getBusinessDocumentId() + "/" + entryName)
                .filename(entryName)
                .contentType(com.example.fileprocessor.domain.util.MimeTypeUtil.getMimeType(entryName))
                .isZip(false)
                .build();
    }

    @Override
    protected Flux<FileUploadResponse> uploadDocument(ProcessingContext<AnimalDocumentHistoryDTO> context, Long docId) {
        AnimalDocumentHistoryDTO history = context.getHistory();
        return homologationRepository.resolve(history)
                .flatMapMany(homologation -> {
                    FileUploadRequest uploadReq = FileUploadRequest.fromAnimal(
                            history, context.getFileContent(), docId, homologation, metadataStrategy);
                    return soapGateway.send(uploadReq);
                });
    }

    @Override
    protected String implementationName() {
        return AnimalDocument.USE_CASE_NAME;
    }

    /**
     * Orquesta el flujo diario de Animales de forma limpia y secuencial.
     * Toda la complejidad de aplanar y filtrar el árbol reside en el Adapter del Gateway.
     */
    public Flux<FileUploadResponse> executeAnimalProcessing() {
        LOGGER.info("Iniciando procesamiento diario Animal...");
        return animalRepository.findAllAnimals()
                .concatMap(animal -> animalRestGateway.getPendingDocumentsForAnimal(animal.getId())
                        .concatMap(doc -> {
                            String traceId = "Animal-" + animal.getId() + "-" + doc.getDocumentId();
                            return processWithTracking(doc, traceId);
                        })
                );
    }
}
