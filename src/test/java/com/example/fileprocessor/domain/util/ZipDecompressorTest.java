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

    @Test
    void decompress_zipWithStoredEntryAndDataDescriptor_decompressesSuccessfully() {
        // Construct a ZIP with one STORED entry containing "Hello", with bit flag 3 (data descriptor) set.
        // This is designed to test the java.util.zip.ZipInputStream limitation:
        // "only DEFLATED entries can have EXT descriptor"
        
        byte[] lfh = {
            0x50, 0x4B, 0x03, 0x04, // signature
            0x0A, 0x00,             // version needed (10)
            0x08, 0x00,             // GP flag (bit 3 set)
            0x00, 0x00,             // method (STORED)
            0x00, 0x00, 0x00, 0x00, // mod time/date
            0x00, 0x00, 0x00, 0x00, // CRC (set to 0)
            0x00, 0x00, 0x00, 0x00, // compressed size (set to 0)
            0x00, 0x00, 0x00, 0x00, // uncompressed size (set to 0)
            0x08, 0x00,             // filename length (8)
            0x00, 0x00              // extra field length (0)
        };
        byte[] filename = "test.txt".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] data = "Hello".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] dd = {
            0x50, 0x4B, 0x07, 0x08, // data descriptor signature
            (byte) 0x82, (byte) 0x89, (byte) 0xD1, (byte) 0xF7, // CRC-32 of "Hello" (0xF7D18982)
            0x05, 0x00, 0x00, 0x00, // compressed size (5)
            0x05, 0x00, 0x00, 0x00  // uncompressed size (5)
        };
        byte[] cdfh = {
            0x50, 0x4B, 0x01, 0x02, // central directory signature
            0x14, 0x00,             // version made by (20)
            0x0A, 0x00,             // version needed (10)
            0x08, 0x00,             // GP flag (bit 3 set)
            0x00, 0x00,             // method (STORED)
            0x00, 0x00, 0x00, 0x00, // mod time/date
            (byte) 0x82, (byte) 0x89, (byte) 0xD1, (byte) 0xF7, // CRC-32 of "Hello" (0xF7D18982)
            0x05, 0x00, 0x00, 0x00, // compressed size (5)
            0x05, 0x00, 0x00, 0x00, // uncompressed size (5)
            0x08, 0x00,             // filename length (8)
            0x00, 0x00,             // extra field length (0)
            0x00, 0x00,             // comment length (0)
            0x00, 0x00,             // disk start (0)
            0x00, 0x00,             // internal attrs
            0x00, 0x00, 0x00, 0x00, // external attrs
            0x00, 0x00, 0x00, 0x00  // local header offset (0)
        };
        byte[] eocd = {
            0x50, 0x4B, 0x05, 0x06, // end of central directory signature
            0x00, 0x00,             // number of this disk
            0x00, 0x00,             // disk where CD starts
            0x01, 0x00,             // total entries on this disk
            0x01, 0x00,             // total entries
            0x36, 0x00, 0x00, 0x00, // size of central directory (54 bytes)
            0x3B, 0x00, 0x00, 0x00, // offset of central directory (59 bytes)
            0x00, 0x00              // comment length (0)
        };

        // Combine all arrays
        int totalLen = lfh.length + filename.length + data.length + dd.length + cdfh.length + filename.length + eocd.length;
        byte[] zipBytes = new byte[totalLen];
        int pos = 0;
        System.arraycopy(lfh, 0, zipBytes, pos, lfh.length); pos += lfh.length;
        System.arraycopy(filename, 0, zipBytes, pos, filename.length); pos += filename.length;
        System.arraycopy(data, 0, zipBytes, pos, data.length); pos += data.length;
        System.arraycopy(dd, 0, zipBytes, pos, dd.length); pos += dd.length;
        System.arraycopy(cdfh, 0, zipBytes, pos, cdfh.length); pos += cdfh.length;
        System.arraycopy(filename, 0, zipBytes, pos, filename.length); pos += filename.length;
        System.arraycopy(eocd, 0, zipBytes, pos, eocd.length);

        DocumentHistoryDTO zipHistory = DocumentHistoryDTO.builder()
            .productId("prod-1")
            .isZip(true)
            .originCountry("AR")
            .businessDocumentId("doc-1")
            .filename("stored_with_descriptor.zip")
            .contentType("application/zip")
            .size((long) zipBytes.length)
            .originFolder("origin")
            .content(zipBytes)
            .build();

        StepVerifier.create(ZipDecompressor.decompress(zipHistory))
            .assertNext(doc -> {
                assertEquals("test.txt", doc.getFilename());
                assertEquals("doc-1/test.txt", doc.getBusinessDocumentId());
                assertArrayEquals("Hello".getBytes(java.nio.charset.StandardCharsets.US_ASCII), doc.getContent());
                assertFalse(Boolean.TRUE.equals(doc.getIsZip()));
            })
            .verifyComplete();
    }
}