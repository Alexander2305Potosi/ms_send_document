package com.example.fileprocessor.domain.entity.animal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimalMaestroTest {

    @Test
    void testAnimalMaestroBuilderAndAccessors() {
        AnimalMaestro animal = AnimalMaestro.builder()
                .id(1L)
                .name("Dog")
                .category("Canine")
                .build();

        assertEquals(1L, animal.getId());
        assertEquals("Dog", animal.getName());
        assertEquals("Canine", animal.getCategory());
    }

    @Test
    void testConstructor() {
        AnimalMaestro animal = new AnimalMaestro(1L, "Cat", "Feline");
        assertEquals(1L, animal.getId());
        assertEquals("Cat", animal.getName());
        assertEquals("Feline", animal.getCategory());
    }
}
