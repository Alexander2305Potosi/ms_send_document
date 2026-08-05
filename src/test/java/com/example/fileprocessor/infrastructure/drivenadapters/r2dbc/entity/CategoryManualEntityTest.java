package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CategoryManualEntityTest {

    @Test
    void testCategoryManualEntityBuilderAndAccessors() {
        CategoryManualEntity entity = CategoryManualEntity.builder()
                .id(1L)
                .prefijo("PREFIX")
                .categoriaHomologado("Mammal")
                .build();

        assertEquals(1L, entity.getId());
        assertEquals("PREFIX", entity.getPrefijo());
        assertEquals("Mammal", entity.getCategoriaHomologado());

        // Test setters
        entity.setId(2L);
        entity.setPrefijo("PREFIX2");
        entity.setCategoriaHomologado("Aves");

        assertEquals(2L, entity.getId());
        assertEquals("PREFIX2", entity.getPrefijo());
        assertEquals("Aves", entity.getCategoriaHomologado());
    }

    @Test
    void testNoArgsConstructor() {
        CategoryManualEntity entity = new CategoryManualEntity();
        assertNull(entity.getId());
        assertNull(entity.getPrefijo());
        assertNull(entity.getCategoriaHomologado());
    }
}
