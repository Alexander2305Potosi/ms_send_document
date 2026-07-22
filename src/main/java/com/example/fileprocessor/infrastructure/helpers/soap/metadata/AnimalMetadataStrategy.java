package com.example.fileprocessor.infrastructure.helpers.soap.metadata;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.infrastructure.helpers.soap.metadata.MetadataStrategy;
import org.springframework.stereotype.Component;

/**
 * Metadata strategy for Animal use case.
 * Generates the metadata block with animal-specific fields: fecha, animalId, raza, tipo.
 */
@Component("animalMetadataStrategy")
public class AnimalMetadataStrategy implements MetadataStrategy {

    @Override
    public String buildMetadataBlock(FileUploadRequest request) {
        String fecha = java.time.LocalDate.now().toString();
        return """
                <dato>
                    <nombre>Bfecha</nombre>
                    <valor>%s</valor>
                </dato>
                <dato>
                    <nombre>BanimalId</nombre>
                    <valor>%s</valor>
                </dato>
                <dato>
                    <nombre>Braza</nombre>
                    <valor>%s</valor>
                </dato>
                <dato>
                    <nombre>Btipo</nombre>
                    <valor>%s</valor>
                </dato>""".formatted(
                escapeXml(fecha),
                escapeXml(request.getAnimalId()),
                escapeXml(request.getRaza()),
                escapeXml(request.getTipo()));
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
