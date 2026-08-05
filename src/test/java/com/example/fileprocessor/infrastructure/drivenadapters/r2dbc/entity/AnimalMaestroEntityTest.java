package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimalMaestroEntityTest {

    @Test
    void testAnimalMaestroEntityBuilderAndGetters() {
        AnimalMaestroEntity entity = AnimalMaestroEntity.builder()
                .id(1L)
                .name("Cat")
                .category("Feline")
                .build();

        assertEquals(1L, entity.getId());
        assertEquals("Cat", entity.getName());
        assertEquals("Feline", entity.getCategory());
    }

    @Test
    void testNoArgsConstructor() {
        AnimalMaestroEntity entity = new AnimalMaestroEntity();
        assertNull(entity.getId());
        assertNull(entity.getName());
        assertNull(entity.getCategory());
    }
}
