package com.example.fileprocessor.domain.util;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.DECOMPRESSION_ERROR;

import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ZipDecompressorTest {

    @TempDir
    Path tempDir;

    private byte[] createTestZip(String entryName, String contentStr) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(contentStr.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createTestZipWithDirectory(String dirName, String entryName, String contentStr) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry dirEntry = new ZipEntry(dirName);
            zos.putNextEntry(dirEntry);
            zos.closeEntry();

            ZipEntry fileEntry = new ZipEntry(entryName);
            zos.putNextEntry(fileEntry);
            zos.write(contentStr.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    @Test
    void decompressWhenNotZipReturnsOriginal() {
        DocumentHistoryDTO dto = DocumentHistoryDTO.builder()
                .isZip(false)
                .filename("test.txt")
                .build();

        StepVerifier.create(ZipDecompressor.decompress(dto, tempDir.toString()))
                .expectNext(dto)
                .verifyComplete();
    }

    @Test
    void decompressWhenContentNullReturnsOriginal() {
        DocumentHistoryDTO dto = DocumentHistoryDTO.builder()
                .isZip(true)
                .filename("test.zip")
                .content(null)
                .build();

        StepVerifier.create(ZipDecompressor.decompress(dto, tempDir.toString()))
                .expectNext(dto)
                .verifyComplete();
    }

    @Test
    void decompressWithValidZipExtractsSuccessfully() throws IOException {
        byte[] zipBytes = createTestZip("inner.txt", "Hello inside ZIP");
        DocumentHistoryDTO dto = DocumentHistoryDTO.builder()
                .isZip(true)
                .filename("test.zip")
                .content(zipBytes)
                .businessDocumentId("doc-1")
                .build();

        StepVerifier.create(ZipDecompressor.decompress(dto, tempDir.toString()))
                .assertNext(extracted -> {
                    assertEquals("inner.txt", extracted.getFilename());
                    assertEquals("Hello inside ZIP", new String(extracted.getContent()));
                    assertFalse(extracted.getIsZip());
                    assertEquals("doc-1/inner.txt", extracted.getBusinessDocumentId());
                })
                .verifyComplete();
    }

    @Test
    void decompressWithZipContainingDirectorySkipsDirectory() throws IOException {
        byte[] zipBytes = createTestZipWithDirectory("some-dir/", "some-dir/inner.txt", "Hello inside ZIP");
        DocumentHistoryDTO dto = DocumentHistoryDTO.builder()
                .isZip(true)
                .filename("test.zip")
                .content(zipBytes)
                .businessDocumentId("doc-1")
                .build();

        StepVerifier.create(ZipDecompressor.decompress(dto, tempDir.toString()))
                .assertNext(extracted -> {
                    assertEquals("some-dir/inner.txt", extracted.getFilename());
                    assertEquals("Hello inside ZIP", new String(extracted.getContent()));
                })
                .verifyComplete();
    }

    @Test
    void decompressWithZipSlipVulnerabilityThrowsProcessingException() throws IOException {
        byte[] zipBytes = createTestZip("../escaped.txt", "Evil content");
        DocumentHistoryDTO dto = DocumentHistoryDTO.builder()
                .isZip(true)
                .filename("test.zip")
                .content(zipBytes)
                .businessDocumentId("doc-1")
                .build();

        ProcessingException ex = assertThrows(ProcessingException.class, () -> {
            ZipDecompressor.decompress(dto, tempDir.toString());
        });
        assertTrue(ex.getMessage().contains("Zip Slip vulnerability detected"));
        assertEquals(DECOMPRESSION_ERROR.name(), ex.getErrorCode());
    }

    @Test
    void decompressWithZipBombSizeTooBigThrowsProcessingException() throws IOException {
        // Create an entry that exceeds max allowed size (50MB) by mocking/writing larger payload
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("huge.txt");
            zos.putNextEntry(entry);
            // write 51MB
            byte[] chunk = new byte[1024 * 1024];
            for (int i = 0; i < 51; i++) {
                zos.write(chunk);
            }
            zos.closeEntry();
        }
        byte[] zipBytes = baos.toByteArray();

        DocumentHistoryDTO dto = DocumentHistoryDTO.builder()
                .isZip(true)
                .filename("test.zip")
                .content(zipBytes)
                .businessDocumentId("doc-1")
                .build();

        ProcessingException ex = assertThrows(ProcessingException.class, () -> {
            ZipDecompressor.decompress(dto, tempDir.toString());
        });
        assertTrue(ex.getMessage().contains("ZIP entry exceeds maximum allowed size"));
        assertEquals(DECOMPRESSION_ERROR.name(), ex.getErrorCode());
    }

    @Test
    void decompressWithZipBombTooManyEntriesThrowsProcessingException() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < 1002; i++) {
                ZipEntry entry = new ZipEntry("file_" + i + ".txt");
                zos.putNextEntry(entry);
                zos.write("data".getBytes());
                zos.closeEntry();
            }
        }
        byte[] zipBytes = baos.toByteArray();

        DocumentHistoryDTO dto = DocumentHistoryDTO.builder()
                .isZip(true)
                .filename("bomb.zip")
                .content(zipBytes)
                .businessDocumentId("doc-1")
                .build();

        ProcessingException ex = assertThrows(ProcessingException.class, () -> {
            ZipDecompressor.decompress(dto, tempDir.toString());
        });
        assertTrue(ex.getMessage().contains("Too many entries in ZIP"));
        assertEquals(DECOMPRESSION_ERROR.name(), ex.getErrorCode());
    }

    @Test
    void decompressWithCorruptedZipThrowsProcessingException() {
        byte[] corruptBytes = "Not a zip file".getBytes();
        DocumentHistoryDTO dto = DocumentHistoryDTO.builder()
                .isZip(true)
                .filename("corrupt.zip")
                .content(corruptBytes)
                .build();

        StepVerifier.create(ZipDecompressor.decompress(dto, tempDir.toString()))
                .expectErrorMatches(throwable -> throwable instanceof ProcessingException
                        && throwable.getMessage().contains("Failed to decompress ZIP")
                        && DECOMPRESSION_ERROR.name().equals(((ProcessingException) throwable).getErrorCode()))
                .verify();
    }

    @Test
    void decompressFallbackPathVerification() throws IOException {
        byte[] zipBytes = createTestZip("inner.txt", "content");
        DocumentHistoryDTO dto = DocumentHistoryDTO.builder()
                .isZip(true)
                .filename("test.zip")
                .content(zipBytes)
                .businessDocumentId("doc-1")
                .build();

        // Pass null or empty temp directory to trigger candidate fallbacks
        StepVerifier.create(ZipDecompressor.decompress(dto, null))
                .assertNext(extracted -> assertEquals("inner.txt", extracted.getFilename()))
                .verifyComplete();

        StepVerifier.create(ZipDecompressor.decompress(dto, "   "))
                .assertNext(extracted -> assertEquals("inner.txt", extracted.getFilename()))
                .verifyComplete();
    }

    @Test
    void privateConstructorCanBeCalledViaReflection() throws Exception {
        Constructor<ZipDecompressor> constructor = ZipDecompressor.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        ZipDecompressor instance = constructor.newInstance();
        assertNotNull(instance);
    }
}