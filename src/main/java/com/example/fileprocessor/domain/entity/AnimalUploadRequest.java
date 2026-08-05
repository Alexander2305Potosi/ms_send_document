package com.example.fileprocessor.domain.entity;

import com.example.fileprocessor.domain.entity.animal.AnimalDocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.homologation.HomologationResult;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Solicitud de carga de archivos para el caso de uso de Animales.
 * Extiende {@link FileUploadRequest} con los campos propios del canal de animales.
 */
@Getter
@SuperBuilder(toBuilder = true)
public class AnimalUploadRequest extends FileUploadRequest {

    /** Identificador del animal maestro en el sistema. */
    private final String animalId;

    /** Raza del animal. */
    private final String raza;

    /** Tipo / especie del animal. */
    private final String tipo;

    /**
     * Crea un {@code AnimalUploadRequest} a partir de los metadatos del documento animal
     * y el resultado de homologación.
     *
     * @param history metadatos del documento animal procesado
     * @param content bytes del archivo
     * @param docId   ID interno del registro en base de datos
     * @param h       resultado de homologación (puede ser {@code null})
     * @return instancia lista para ser enviada al gateway SOAP de animales
     */
    public static AnimalUploadRequest from(AnimalDocumentHistoryDTO history,
                                           byte[] content,
                                           Long docId,
                                           HomologationResult h) {
        return AnimalUploadRequest.builder()
                .documentId(history.getBusinessDocumentId())
                .content(content != null ? content : new byte[0])
                .filename(history.getFilename())
                .contentType(history.getContentType())
                .fileSize(history.getSize() != null ? history.getSize() : 0)
                .originFolder(history.getOriginFolder())
                .categoriaDocument(h != null ? h.categoriaDocument() : history.getBusinessDocumentId())
                .homologationFolder(h != null && h.homologationCountry() != null
                        ? h.homologationCountry().homologationFolder()
                        : history.getOriginFolder())
                .homologationCountry(h != null && h.homologationCountry() != null
                        ? h.homologationCountry().homologationCountry()
                        : history.getOriginCountry())
                .docId(docId)
                .useCase(history.getUseCase())
                // Campos específicos del canal Animal
                .animalId(history.getAnimalId())
                .raza(history.getRaza())
                .tipo(history.getTipo())
                .build();
    }
}
