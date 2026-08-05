package com.example.fileprocessor.infrastructure.helpers.soap.metadata;

import com.example.fileprocessor.domain.entity.AnimalUploadRequest;
import org.springframework.stereotype.Component;

/**
 * Estrategia de metadatos para el caso de uso de Animales.
 * Genera el bloque de metadatos con los campos propios del canal:
 * {@code fecha}, {@code animalId}, {@code raza}, {@code tipo}.
 *
 * <p>Esta estrategia recibe directamente un {@link AnimalUploadRequest},
 * por lo que accede a los campos específicos de forma segura y tipada.</p>
 */
@Component
public class AnimalMetadataStrategy {

    /**
     * Construye el bloque XML de metadatos para el envelope SOAP del canal Animal.
     *
     * @param request solicitud de carga con los campos del animal
     * @return fragmento XML crudo para insertar dentro de {@code <metaData>}
     */
    public String buildMetadataBlock(AnimalUploadRequest request) {
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
