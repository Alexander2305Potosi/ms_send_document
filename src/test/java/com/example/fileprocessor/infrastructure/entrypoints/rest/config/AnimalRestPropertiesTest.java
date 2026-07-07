package com.example.fileprocessor.infrastructure.entrypoints.rest.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimalRestPropertiesTest {

    @Test
    void testAnimalRestPropertiesAccessors() {
        AnimalRestProperties properties = new AnimalRestProperties(
                "http://localhost:8080",
                "/animals",
                "/directories",
                10
        );

        assertEquals("http://localhost:8080", properties.endpoint());
        assertEquals("/animals", properties.animalDirectoryPath());
        assertEquals("/directories", properties.directoryTreePath());
        assertEquals(10, properties.timeoutSeconds());
    }
}
