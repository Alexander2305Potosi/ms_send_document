package com.example.fileprocessor.infrastructure.drivenadapters.masterdb.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class ProductMasterEntityTest {

    @Test
    void testProductMasterEntity() {
        LocalDateTime now = LocalDateTime.now();
        ProductMasterEntity entity = ProductMasterEntity.builder()
                .id(1L)
                .productId("PROD1")
                .nombre("Test")
                .fechaCargue(now)
                .estado("ACTIVO")
                .carpetaOrigen("folder1")
                .paisOrigen("CO")
                .build();

        assertEquals(1L, entity.getId());
        assertEquals("PROD1", entity.getProductId());
        assertEquals("Test", entity.getNombre());
        assertEquals(now, entity.getFechaCargue());
        assertEquals("ACTIVO", entity.getEstado());
        assertEquals("folder1", entity.getCarpetaOrigen());
        assertEquals("CO", entity.getPaisOrigen());

        entity.setId(2L);
        assertEquals(2L, entity.getId());

        ProductMasterEntity entity2 = new ProductMasterEntity();
        assertNull(entity2.getId());
        
        ProductMasterEntity entity3 = new ProductMasterEntity(3L, "P3", "N3", now, "ACTIVO", "f3", "CO");
        assertNotNull(entity3.toString());
        assertEquals(entity3, entity3);
        assertNotEquals(entity, entity3);
        assertNotNull(entity3.hashCode());
    }
}
