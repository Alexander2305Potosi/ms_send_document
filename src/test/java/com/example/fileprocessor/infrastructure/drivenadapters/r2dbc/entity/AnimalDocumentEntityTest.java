package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class AnimalDocumentEntityTest {

    @Test
    void testAnimalDocumentEntityBuilderAndAccessors() {
        LocalDateTime now = LocalDateTime.now();
        AnimalDocumentEntity entity = AnimalDocumentEntity.builder()
                .id(1L)
                .documentId("doc-1")
                .productId("prod-1")
                .name("test.pdf")
                .state("PENDING")
                .syncMessage("Message")
                .isZip(false)
                .useCase("Animal")
                .originFolder("origin")
                .originCountry("US")
                .homologationFolder("homologated")
                .homologationCountry("MX")
                .categoriaHomologada("Mammal")
                .sucursal("suc-1")
                .retryCount(2)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(1L, entity.getId());
        assertEquals("doc-1", entity.getDocumentId());
        assertEquals("prod-1", entity.getProductId());
        assertEquals("test.pdf", entity.getName());
        assertEquals("PENDING", entity.getState());
        assertEquals("Message", entity.getSyncMessage());
        assertFalse(entity.getIsZip());
        assertEquals("Animal", entity.getUseCase());
        assertEquals("origin", entity.getOriginFolder());
        assertEquals("US", entity.getOriginCountry());
        assertEquals("homologated", entity.getHomologationFolder());
        assertEquals("MX", entity.getHomologationCountry());
        assertEquals("Mammal", entity.getCategoriaHomologada());
        assertEquals("suc-1", entity.getSucursal());
        assertEquals(2, entity.getRetryCount());
        assertEquals(now, entity.getCreatedAt());
        assertEquals(now, entity.getUpdatedAt());

        // Test setters
        entity.setId(2L);
        entity.setDocumentId("doc-2");
        entity.setProductId("prod-2");
        entity.setName("test2.pdf");
        entity.setState("PROCESSED");
        entity.setSyncMessage("Message2");
        entity.setIsZip(true);
        entity.setUseCase("Product");
        entity.setOriginFolder("origin2");
        entity.setOriginCountry("CA");
        entity.setHomologationFolder("homologated2");
        entity.setHomologationCountry("US");
        entity.setCategoriaHomologada("Aves");
        entity.setSucursal("suc-2");
        entity.setRetryCount(3);
        
        assertEquals(2L, entity.getId());
        assertEquals("doc-2", entity.getDocumentId());
        assertEquals("prod-2", entity.getProductId());
        assertEquals("test2.pdf", entity.getName());
        assertEquals("PROCESSED", entity.getState());
        assertEquals("Message2", entity.getSyncMessage());
        assertTrue(entity.getIsZip());
        assertEquals("Product", entity.getUseCase());
        assertEquals("origin2", entity.getOriginFolder());
        assertEquals("CA", entity.getOriginCountry());
        assertEquals("homologated2", entity.getHomologationFolder());
        assertEquals("US", entity.getHomologationCountry());
        assertEquals("Aves", entity.getCategoriaHomologada());
        assertEquals("suc-2", entity.getSucursal());
        assertEquals(3, entity.getRetryCount());
    }

    @Test
    void testNoArgsConstructor() {
        AnimalDocumentEntity entity = new AnimalDocumentEntity();
        assertNull(entity.getId());
        assertNull(entity.getDocumentId());
        assertNull(entity.getProductId());
        assertNull(entity.getName());
        assertNull(entity.getState());
    }
}
