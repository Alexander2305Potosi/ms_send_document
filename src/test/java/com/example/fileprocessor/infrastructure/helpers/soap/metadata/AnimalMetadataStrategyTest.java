package com.example.fileprocessor.infrastructure.helpers.soap.metadata;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnimalMetadataStrategyTest {

    @Test
    void buildMetadataBlock_withValidData_generatesCorrectXml() {
        AnimalMetadataStrategy strategy = new AnimalMetadataStrategy();
        FileUploadRequest request = FileUploadRequest.builder()
                .animalId("A123")
                .raza("Holstein")
                .tipo("Vaca")
                .build();

        String result = strategy.buildMetadataBlock(request);

        assertTrue(result.contains("<nombre>Bfecha</nombre>"));
        assertTrue(result.contains("<nombre>BanimalId</nombre>"));
        assertTrue(result.contains("<valor>A123</valor>"));
        assertTrue(result.contains("<nombre>Braza</nombre>"));
        assertTrue(result.contains("<valor>Holstein</valor>"));
        assertTrue(result.contains("<nombre>Btipo</nombre>"));
        assertTrue(result.contains("<valor>Vaca</valor>"));
    }

    @Test
    void buildMetadataBlock_withSpecialChars_escapesXml() {
        AnimalMetadataStrategy strategy = new AnimalMetadataStrategy();
        FileUploadRequest request = FileUploadRequest.builder()
                .animalId("A<123>")
                .raza("Hol&stein")
                .tipo("V'ac\"a")
                .build();

        String result = strategy.buildMetadataBlock(request);

        assertTrue(result.contains("<valor>A&lt;123&gt;</valor>"));
        assertTrue(result.contains("<valor>Hol&amp;stein</valor>"));
        assertTrue(result.contains("<valor>V&apos;ac&quot;a</valor>"));
    }

    @Test
    void buildMetadataBlock_withNullValues_handlesNullGracefully() {
        AnimalMetadataStrategy strategy = new AnimalMetadataStrategy();
        FileUploadRequest request = FileUploadRequest.builder().build();

        String result = strategy.buildMetadataBlock(request);

        assertTrue(result.contains("<valor></valor>"));
    }
}
