package com.example.fileprocessor.domain.util;

import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ZipDecompressorTest {

    @Test
    void decompress_nonZip_returnsSameDocument() {
        DocumentHistoryDTO history = DocumentHistoryDTO.builder()
            .productId("prod-1")
            .isZip(false)
            .pais("AR")
            .businessDocumentId("doc-1")
            .filename("test.pdf")
            .contentType("application/pdf")
            .size(1L)
            .origin("origin")
            .content(new byte[]{1})
            .build();

        StepVerifier.create(ZipDecompressor.decompress(history))
            .expectNextMatches(result ->
                result.getBusinessDocumentId().equals("doc-1") &&
                result.getFilename().equals("test.pdf") &&
                !Boolean.TRUE.equals(result.getIsZip()))
            .verifyComplete();
    }

    @Test
    void decompress_zipWithTwoFiles_expandsToTwoDocuments() throws IOException {
        byte[] zipContent = createZip("test.pdf", new byte[]{1}, "data.csv", new byte[]{2});

        DocumentHistoryDTO zipHistory = DocumentHistoryDTO.builder()
            .productId("prod-1")
            .isZip(true)
            .pais("AR")
            .businessDocumentId("doc-1")
            .filename("docs.zip")
            .contentType("application/zip")
            .size((long) zipContent.length)
            .origin("origin")
            .content(zipContent)
            .build();

        Flux<DocumentHistoryDTO> result = ZipDecompressor.decompress(zipHistory);

        StepVerifier.create(result)
            .assertNext(doc -> {
                assertTrue(doc.getFilename().endsWith("test.pdf"));
                assertEquals("doc-1/test.pdf", doc.getBusinessDocumentId());
                assertFalse(Boolean.TRUE.equals(doc.getIsZip()));
                assertEquals("AR", doc.getPais());
            })
            .assertNext(doc -> {
                assertTrue(doc.getFilename().endsWith("data.csv"));
                assertEquals("doc-1/data.csv", doc.getBusinessDocumentId());
                assertFalse(Boolean.TRUE.equals(doc.getIsZip()));
                assertEquals("AR", doc.getPais());
            })
            .verifyComplete();
    }

    @Test
    void decompress_emptyZip_returnsEmptyFlux() throws IOException {
        byte[] emptyZip = createEmptyZip();

        DocumentHistoryDTO zipHistory = DocumentHistoryDTO.builder()
            .productId("prod-1")
            .isZip(true)
            .pais("AR")
            .businessDocumentId("doc-1")
            .filename("empty.zip")
            .contentType("application/zip")
            .size((long) emptyZip.length)
            .origin("origin")
            .content(emptyZip)
            .build();

        StepVerifier.create(ZipDecompressor.decompress(zipHistory))
            .verifyComplete();
    }

    private byte[] createZip(String name1, byte[] content1, String name2, byte[] content2) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(name1));
            zos.write(content1);
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(name2));
            zos.write(content2);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createEmptyZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.flush(); 
        }
        return baos.toByteArray();
    }
}