package com.example.fileprocessor.infrastructure.helpers.soap.metadata;

import com.example.fileprocessor.domain.entity.FileUploadRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductMetadataStrategyTest {

    @Test
    void buildMetadataBlock_generatesCorrectXml() {
        ProductMetadataStrategy strategy = new ProductMetadataStrategy();
        FileUploadRequest request = FileUploadRequest.builder().build();

        String result = strategy.buildMetadataBlock(request);

        assertTrue(result.contains("<nombre>Bfecha</nombre>"));
        assertTrue(result.contains("<nombre>Bcomentario</nombre>"));
        assertTrue(result.contains("<valor>Procesamiento automatico</valor>"));
    }
}
