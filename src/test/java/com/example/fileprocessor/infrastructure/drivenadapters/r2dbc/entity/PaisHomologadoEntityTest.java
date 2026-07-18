package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaisHomologadoEntityTest {

    @Test
    void testPaisHomologadoEntityBuilderAndAccessors() {
        PaisHomologadoEntity entity = PaisHomologadoEntity.builder()
                .id(1L)
                .orden(10)
                .condicionJsonb("[]")
                .homologationFolder("folder")
                .homologationCountry("MX")
                .build();

        assertEquals(1L, entity.getId());
        assertEquals(10, entity.getOrden());
        assertEquals("[]", entity.getCondicionJsonb());
        assertEquals("folder", entity.getHomologationFolder());
        assertEquals("MX", entity.getHomologationCountry());

        // Test setters
        entity.setId(2L);
        entity.setOrden(20);
        entity.setCondicionJsonb("{}");
        entity.setHomologationFolder("folder2");
        entity.setHomologationCountry("US");

        assertEquals(2L, entity.getId());
        assertEquals(20, entity.getOrden());
        assertEquals("{}", entity.getCondicionJsonb());
        assertEquals("folder2", entity.getHomologationFolder());
        assertEquals("US", entity.getHomologationCountry());
    }

    @Test
    void testNoArgsConstructor() {
        PaisHomologadoEntity entity = new PaisHomologadoEntity();
        assertNull(entity.getId());
        assertNull(entity.getOrden());
        assertNull(entity.getCondicionJsonb());
        assertNull(entity.getHomologationFolder());
        assertNull(entity.getHomologationCountry());
    }
}
