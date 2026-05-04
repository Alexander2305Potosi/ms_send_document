package com.example.fileprocessor.domain.util;

import com.example.fileprocessor.domain.entity.ProductDocumentHistory;
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
        ProductDocumentHistory doc = new ProductDocumentHistory(
            "prod-1", false, "AR",
            null, "doc-1", null, null,
            "test.pdf", null, null,
            null, null, null, null,
            "test.pdf", "application/pdf", 1L, "origin",
            new byte[]{1}, null
        );

        StepVerifier.create(ZipDecompressor.decompress(doc))
            .expectNextMatches(result ->
                result.documentId().equals("doc-1") &&
                result.filename().equals("test.pdf") &&
                !result.isZip())
            .verifyComplete();
    }

    @Test
    void decompress_zipWithTwoFiles_expandsToTwoDocuments() throws IOException {
        byte[] zipContent = createZip("test.pdf", new byte[]{1}, "data.csv", new byte[]{2});

        ProductDocumentHistory zipDoc = new ProductDocumentHistory(
            "prod-1", true, "AR",
            null, "doc-1", null, null,
            "docs.zip", null, null,
            null, null, null, null,
            "docs.zip", "application/zip", (long) zipContent.length, "origin",
            zipContent, null
        );

        Flux<ProductDocumentHistory> result = ZipDecompressor.decompress(zipDoc);

        StepVerifier.create(result)
            .assertNext(doc -> {
                assertTrue(doc.filename().endsWith("test.pdf"));
                assertEquals("doc-1/test.pdf", doc.documentId());
                assertFalse(doc.isZip());
                assertEquals("AR", doc.pais());
                assertEquals("docs.zip", doc.parentZipName());
            })
            .assertNext(doc -> {
                assertTrue(doc.filename().endsWith("data.csv"));
                assertEquals("doc-1/data.csv", doc.documentId());
                assertFalse(doc.isZip());
                assertEquals("AR", doc.pais());
                assertEquals("docs.zip", doc.parentZipName());
            })
            .verifyComplete();
    }

    @Test
    void decompress_emptyZip_returnsEmptyFlux() throws IOException {
        byte[] emptyZip = createEmptyZip();

        ProductDocumentHistory zipDoc = new ProductDocumentHistory(
            "prod-1", true, "AR",
            null, "doc-1", null, null,
            "empty.zip", null, null,
            null, null, null, null,
            "empty.zip", "application/zip", (long) emptyZip.length, "origin",
            emptyZip, null
        );

        StepVerifier.create(ZipDecompressor.decompress(zipDoc))
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
            // empty ZIP
        }
        return baos.toByteArray();
    }
}