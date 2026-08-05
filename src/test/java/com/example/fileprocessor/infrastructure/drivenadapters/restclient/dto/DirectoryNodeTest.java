package com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DirectoryNodeTest {

    @Test
    void testDirectoryNodeBuilderAndAccessors() {
        DirectoryNode child = DirectoryNode.builder()
                .id("child1")
                .name("child")
                .source(1)
                .productId("p1")
                .businessDocumentId("b1")
                .build();

        DirectoryNode parent = DirectoryNode.builder()
                .id("parent1")
                .name("parent")
                .children(List.of(child))
                .build();

        assertEquals("parent1", parent.getId());
        assertEquals("parent", parent.getName());
        assertEquals(1, parent.getChildren().size());
        assertEquals(child, parent.getChildren().get(0));
    }
}
