package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class AnimalDocumentHistoryEntityTest {

    @Test
    void testAnimalDocumentHistoryEntityBuilderAndAccessors() {
        Instant now = Instant.now();
        AnimalDocumentHistoryEntity entity = AnimalDocumentHistoryEntity.builder()
                .id(1L)
                .documentId(10L)
                .filename("test.pdf")
                .useCase("Animal")
                .result("SUCCESS")
                .syncStatus("OK")
                .syncMessage("Sync message")
                .retry(1)
                .startedAt(now)
                .completedAt(now)
                .build();

        assertEquals(1L, entity.getId());
        assertEquals(10L, entity.getDocumentId());
        assertEquals("test.pdf", entity.getFilename());
        assertEquals("Animal", entity.getUseCase());
        assertEquals("SUCCESS", entity.getResult());
        assertEquals("OK", entity.getSyncStatus());
        assertEquals("Sync message", entity.getSyncMessage());
        assertEquals(1, entity.getRetry());
        assertEquals(now, entity.getStartedAt());
        assertEquals(now, entity.getCompletedAt());

        // Test setters
        entity.setId(2L);
        entity.setDocumentId(20L);
        entity.setFilename("test2.pdf");
        entity.setUseCase("Product");
        entity.setResult("FAILURE");
        entity.setSyncStatus("ERROR");
        entity.setSyncMessage("Error message");
        entity.setRetry(2);
        
        assertEquals(2L, entity.getId());
        assertEquals(20L, entity.getDocumentId());
        assertEquals("test2.pdf", entity.getFilename());
        assertEquals("Product", entity.getUseCase());
        assertEquals("FAILURE", entity.getResult());
        assertEquals("ERROR", entity.getSyncStatus());
        assertEquals("Error message", entity.getSyncMessage());
        assertEquals(2, entity.getRetry());
    }

    @Test
    void testNoArgsConstructor() {
        AnimalDocumentHistoryEntity entity = new AnimalDocumentHistoryEntity();
        assertNull(entity.getId());
        assertNull(entity.getDocumentId());
        assertNull(entity.getFilename());
        assertNull(entity.getUseCase());
        assertNull(entity.getResult());
    }
}
