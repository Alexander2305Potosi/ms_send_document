package com.example.fileprocessor.infrastructure.helpers.soap.metadata;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import com.example.fileprocessor.infrastructure.helpers.soap.metadata.MetadataStrategy;
import org.springframework.stereotype.Component;

/**
 * Metadata strategy for Product (SOAP) use case.
 * Generates the default metadata block: fecha + comentario.
 */
@Component("productMetadataStrategy")
public class ProductMetadataStrategy implements MetadataStrategy {

    @Override
    public String buildMetadataBlock(FileUploadRequest request) {
        String fecha = java.time.LocalDate.now().toString();
        return """
                <dato>
                    <nombre>Bfecha</nombre>
                    <valor>%s</valor>
                </dato>
                <dato>
                    <nombre>Bcomentario</nombre>
                    <valor>Procesamiento automatico</valor>
                </dato>""".formatted(escapeXml(fecha));
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
