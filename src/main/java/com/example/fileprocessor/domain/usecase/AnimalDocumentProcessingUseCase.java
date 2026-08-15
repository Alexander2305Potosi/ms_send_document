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

    @Override
    protected String implementationName() {
        return AnimalDocument.USE_CASE_NAME;
    }

    public Flux<FileUploadResponse> executePendingDocuments() {
        LOGGER.info("Iniciando procesamiento diario Animal...");
        return animalDocumentProvider.getAllPendingAnimalDocuments()
                .concatMap(doc -> {
                    var traceId = "Animal-" + doc.getAnimalId() + "-" + doc.getDocumentId();
                    return processWithTracking(doc, traceId);
                });
    }
}
