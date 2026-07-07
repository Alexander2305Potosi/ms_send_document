package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.domain.entity.FileUploadResponse;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.port.out.DocumentPersistenceGateway;
import com.example.fileprocessor.domain.port.out.HomologationRepository;
import com.example.fileprocessor.domain.port.out.ProductRestGateway;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.domain.port.out.SoapGateway;
import com.example.fileprocessor.domain.port.out.AnimalRepository;
import com.example.fileprocessor.domain.port.out.AnimalRestGateway;
import reactor.core.publisher.Flux;
import java.util.logging.Logger;

/**
 * Caso de uso específico para las reglas de negocio de carga (upload) de Animales.
 * Se mantiene limpio y enfocado al igual que S3 y Soap, definiendo solo el canal de envío.
 */
public class AnimalDocumentProcessingUseCase extends AbstractDocumentProcessingUseCase {

    private static final Logger LOGGER = Logger.getLogger(AnimalDocumentProcessingUseCase.class.getName());

    private final AnimalRepository animalRepository;
    private final AnimalRestGateway animalRestGateway;
    private final SoapGateway soapGateway;
    private final HomologationRepository homologationRepository;

    public AnimalDocumentProcessingUseCase(
            DocumentPersistenceGateway persistencePort,
            ProductRestGateway productRestGateway,
            RulesBussinesGateway documentValidator,
            String tempDirPath,
            AnimalRepository animalRepository,
            AnimalRestGateway animalRestGateway,
            SoapGateway soapGateway,
            HomologationRepository homologationRepository) {
        super(persistencePort, productRestGateway, documentValidator, tempDirPath);
        this.animalRepository = animalRepository;
        this.animalRestGateway = animalRestGateway;
        this.soapGateway = soapGateway;
        this.homologationRepository = homologationRepository;
    }

    @Override
    protected Flux<FileUploadResponse> uploadDocument(DocumentHistoryDTO history, Long docId) {
        return homologationRepository.resolve(history)
                .flatMapMany(homologation -> {
                    FileUploadRequest uploadReq = FileUploadRequest.from(history, null, homologation);
                    return soapGateway.send(uploadReq);
                });
    }

    @Override
    protected String implementationName() {
        return "Animal";
    }

    /**
     * Orquesta el flujo diario de Animales de forma limpia.
     * Toda la complejidad de aplanar y filtrar el árbol reside en el Adapter del Gateway.
     */
    public Flux<FileUploadResponse> executeAnimalProcessing() {
        LOGGER.info("Iniciando procesamiento diario Animal...");
        return animalRepository.findAllAnimals()
                .flatMap(animal -> animalRestGateway.getPendingDocumentsForAnimal(animal.getId()) // Retorna un Flux<Document> ya aplanado y filtrado
                        .flatMap(doc -> {
                            String traceId = "Animal-" + animal.getId() + "-" + doc.getDocumentId();
                            return processWithTracking(doc, traceId); // processWithTracking guarda el historial en historico_documentos
                        })
                );
    }
}
