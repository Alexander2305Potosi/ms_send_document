package com.example.fileprocessor.infrastructure.drivenadapters.restclient;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto.DirectoryNode;
import com.example.fileprocessor.infrastructure.entrypoints.rest.config.AnimalRestProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;
import java.util.Map;

class AnimalRestGatewayAdapterTest {

    private MockWebServer mockWebServer;
    private AnimalRestGatewayAdapter adapter;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        AnimalRestProperties properties = new AnimalRestProperties(
                mockWebServer.url("/").toString(),
                "animals/{animalId}/directory",
                "directories/{directoryId}/tree",
                5
        );
        adapter = new AnimalRestGatewayAdapter(WebClient.builder(), properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void getPendingDocumentsForAnimalSuccessAndFiltersCorrectly() throws Exception {
        // Enqueue Directory ID response
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(objectMapper.writeValueAsString(Map.of("directoryId", "dir-123"))));

        // Create a tree with varied Source values
        DirectoryNode leaf1 = DirectoryNode.builder().id("l1").name("doc1.pdf").source(1).businessDocumentId("b1").productId("p1").build();
        DirectoryNode leaf2 = DirectoryNode.builder().id("l2").name("doc2.pdf").source(2).businessDocumentId("b2").productId("p2").build();
        DirectoryNode leaf3 = DirectoryNode.builder().id("l3").name("doc3.pdf").source(3).businessDocumentId("b3").productId("p3").build(); // Filtered out
        DirectoryNode leaf4 = DirectoryNode.builder().id("l4").name("doc4.pdf").source(4).businessDocumentId("b4").productId("p4").build();
        DirectoryNode leaf5 = DirectoryNode.builder().id("l5").name("doc5.pdf").source(null).businessDocumentId("b5").productId("p5").build(); // Filtered out

        DirectoryNode innerFolder = DirectoryNode.builder().id("f2").name("folder2").children(List.of(leaf3, leaf4, leaf5)).build();
        DirectoryNode root = DirectoryNode.builder().id("f1").name("folder1").children(List.of(leaf1, leaf2, innerFolder)).build();

        // Enqueue Tree response
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(objectMapper.writeValueAsString(root)));

        StepVerifier.create(adapter.getPendingDocumentsForAnimal(1L))
                .expectNextMatches(doc -> doc.getDocumentId().equals("b1") && doc.getName().equals("doc1.pdf"))
                .expectNextMatches(doc -> doc.getDocumentId().equals("b2") && doc.getName().equals("doc2.pdf"))
                .expectNextMatches(doc -> doc.getDocumentId().equals("b4") && doc.getName().equals("doc4.pdf"))
                .expectComplete()
                .verify();
    }

    @Test
    void getPendingDocumentsForAnimalReturnsEmptyOnError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(adapter.getPendingDocumentsForAnimal(1L))
                .expectComplete()
                .verify();
    }
}
