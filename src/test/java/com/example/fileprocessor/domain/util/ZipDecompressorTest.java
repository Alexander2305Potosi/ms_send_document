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
            .originCountry("AR")
            .businessDocumentId("doc-1")
            .filename("test.pdf")
            .contentType("application/pdf")
            .size(1L)
            .originFolder("origin")
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
            .originCountry("AR")
            .businessDocumentId("doc-1")
            .filename("docs.zip")
            .contentType("application/zip")
            .size((long) zipContent.length)
            .originFolder("origin")
            .content(zipContent)
            .build();

        Flux<DocumentHistoryDTO> result = ZipDecompressor.decompress(zipHistory);

        StepVerifier.create(result)
            .assertNext(doc -> {
                assertTrue(doc.getFilename().endsWith("test.pdf"));
                assertEquals("doc-1/test.pdf", doc.getBusinessDocumentId());
                assertFalse(Boolean.TRUE.equals(doc.getIsZip()));
                assertEquals("AR", doc.getOriginCountry());
            })
            .assertNext(doc -> {
                assertTrue(doc.getFilename().endsWith("data.csv"));
                assertEquals("doc-1/data.csv", doc.getBusinessDocumentId());
                assertFalse(Boolean.TRUE.equals(doc.getIsZip()));
                assertEquals("AR", doc.getOriginCountry());
            })
            .verifyComplete();
    }

    @Test
    void decompress_emptyZip_returnsEmptyFlux() throws IOException {
        byte[] emptyZip = createEmptyZip();

        DocumentHistoryDTO zipHistory = DocumentHistoryDTO.builder()
            .productId("prod-1")
            .isZip(true)
            .originCountry("AR")
            .businessDocumentId("doc-1")
            .filename("empty.zip")
            .contentType("application/zip")
            .size((long) emptyZip.length)
            .originFolder("origin")
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

    @Test
    void decompress_corruptZip_throwsProcessingException() {
        DocumentHistoryDTO zipHistory = DocumentHistoryDTO.builder()
            .productId("prod-1")
            .isZip(true)
            .originCountry("AR")
            .businessDocumentId("doc-1")
            .filename("corrupt.zip")
            .contentType("application/zip")
            .size(10L)
            .originFolder("origin")
            .content(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
            .build();

        StepVerifier.create(ZipDecompressor.decompress(zipHistory))
            .expectErrorMatches(throwable -> throwable instanceof com.example.fileprocessor.domain.exception.ProcessingException &&
                ((com.example.fileprocessor.domain.exception.ProcessingException) throwable).getErrorCode()
                    .equals(com.example.fileprocessor.domain.usecase.ProcessingResultCodes.DECOMPRESSION_ERROR.name()))
            .verify();
    }

    @Test
    void decompress_corruptZipWithHeaders_throwsProcessingException() {
        DocumentHistoryDTO zipHistory = DocumentHistoryDTO.builder()
            .productId("prod-1")
            .isZip(true)
            .originCountry("AR")
            .businessDocumentId("doc-1")
            .filename("corrupt_headers.zip")
            .contentType("application/zip")
            .size(10L)
            .originFolder("origin")
            .content(new byte[]{0x50, 0x4B, 0x03, 0x04, 5, 6, 7, 8, 9, 10})
            .build();

        StepVerifier.create(ZipDecompressor.decompress(zipHistory))
            .expectErrorMatches(throwable -> throwable instanceof com.example.fileprocessor.domain.exception.ProcessingException &&
                ((com.example.fileprocessor.domain.exception.ProcessingException) throwable).getErrorCode()
                    .equals(com.example.fileprocessor.domain.usecase.ProcessingResultCodes.DECOMPRESSION_ERROR.name()))
            .verify();
    }
}