package com.example.fileprocessor.domain.entity;

import com.example.fileprocessor.domain.entity.homologation.HomologationResult;
import com.example.fileprocessor.domain.entity.product.BaseDocumentHistoryDTO;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Solicitud de carga de archivos para el caso de uso de Productos (SOAP / S3).
 * Hereda todos los campos comunes de {@link FileUploadRequest}.
 */
@Getter
@SuperBuilder(toBuilder = true)
public class ProductUploadRequest extends FileUploadRequest {

    /**
     * Crea un {@code ProductUploadRequest} a partir de los metadatos del documento
     * y el resultado de homologación.
     *
     * @param history metadatos del documento procesado
     * @param content bytes del archivo
     * @param docId   ID interno del registro en base de datos
     * @param h       resultado de homologación (puede ser {@code null})
     * @return instancia lista para ser enviada al gateway
     */
    public static ProductUploadRequest from(BaseDocumentHistoryDTO history,
                                            byte[] content,
                                            Long docId,
                                            HomologationResult h) {
        return ProductUploadRequest.builder()
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
                .build();
    }
}
