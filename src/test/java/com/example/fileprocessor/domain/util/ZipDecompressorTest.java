package com.example.fileprocessor.domain.util;

import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.DECOMPRESSION_ERROR;

import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.entity.product.ProcessingContext;
import com.example.fileprocessor.domain.exception.ProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ZipDecompressorTest {

    @TempDir
    Path tempDir;

    private final BiFunction<DocumentHistoryDTO, String, DocumentHistoryDTO> entryMapper = (zipHistory, entryName) -> 
        zipHistory.toBuilder()
            .businessDocumentId(zipHistory.getBusinessDocumentId() + "/" + entryName)
            .filename(entryName)
            .isZip(false)
            .build();

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
        ProcessingContext<DocumentHistoryDTO> ctx = new ProcessingContext<>(dto, new byte[]{1});

        StepVerifier.create(ZipDecompressor.decompress(ctx, tempDir.toString(), entryMapper))
                .expectNext(ctx)
                .verifyComplete();
    }

    @Test
    void decompressWhenContentNullReturnsOriginal() {
        DocumentHistoryDTO dto = DocumentHistoryDTO.builder()
                .isZip(true)
                .filename("test.zip")
                .build();
        ProcessingContext<DocumentHistoryDTO> ctx = new ProcessingContext<>(dto, null);

        StepVerifier.create(ZipDecompressor.decompress(ctx, tempDir.toString(), entryMapper))
                .expectNext(ctx)
                .verifyComplete();
    }

    @Test
    void decompressWithValidZipExtractsSuccessfully() throws IOException {
        byte[] zipBytes = createTestZip("inner.txt", "Hello inside ZIP");
        DocumentHistoryDTO dto = DocumentHistoryDTO.builder()
                .isZip(true)
                .filename("test.zip")
                .businessDocumentId("doc-1")
                .build();
        ProcessingContext<DocumentHistoryDTO> ctx = new ProcessingContext<>(dto, zipBytes);

        StepVerifier.create(ZipDecompressor.decompress(ctx, tempDir.toString(), entryMapper))
                .assertNext(extracted -> {
                    assertEquals("inner.txt", extracted.getHistory().getFilename());
                    assertEquals("Hello inside ZIP", new String(extracted.getFileContent()));
                    assertFalse(extracted.getHistory().getIsZip());
                    assertEquals("doc-1/inner.txt", extracted.getHistory().getBusinessDocumentId());
                })
                .verifyComplete();
    }

    @Test
    void decompressWithZipContainingDirectorySkipsDirectory() throws IOException {
        byte[] zipBytes = createTestZipWithDirectory("some-dir/", "some-dir/inner.txt", "Hello inside ZIP");
        DocumentHistoryDTO dto = DocumentHistoryDTO.builder()
                .isZip(true)
                .filename("test.zip")
                .businessDocumentId("doc-1")
                .build();
        ProcessingContext<DocumentHistoryDTO> ctx = new ProcessingContext<>(dto, zipBytes);

        StepVerifier.create(ZipDecompressor.decompress(ctx, tempDir.toString(), entryMapper))
                .assertNext(extracted -> {
                    assertEquals("some-dir/inner.txt", extracted.getHistory().getFilename());
                    assertEquals("Hello inside ZIP", new String(extracted.getFileContent()));
                })
                .verifyComplete();
    }

    @Test
    void decompressWithZipSlipVulnerabilityThrowsProcessingException() throws IOException {
        byte[] zipBytes = createTestZip("../escaped.txt", "Evil content");
        DocumentHistoryDTO dto = DocumentHistoryDTO.builder()
                .isZip(true)
                .filename("test.zip")
                .businessDocumentId("doc-1")
                .build();
        ProcessingContext<DocumentHistoryDTO> ctx = new ProcessingContext<>(dto, zipBytes);

        ProcessingException ex = assertThrows(ProcessingException.class, () -> {
            ZipDecompressor.decompress(ctx, tempDir.toString(), entryMapper).blockLast();
        });
        assertTrue(ex.getMessage().contains("Zip Slip vulnerability detected"));
        assertEquals(DECOMPRESSION_ERROR.name(), ex.getErrorCode());
    }

    @Test
    void decompressWithZipBombSizeTooBigThrowsProcessingException() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("huge.txt");
            zos.putNextEntry(entry);
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
                .businessDocumentId("doc-1")
                .build();
        ProcessingContext<DocumentHistoryDTO> ctx = new ProcessingContext<>(dto, zipBytes);

        ProcessingException ex = assertThrows(ProcessingException.class, () -> {
            ZipDecompressor.decompress(ctx, tempDir.toString(), entryMapper).blockLast();
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
                .businessDocumentId("doc-1")
                .build();
        ProcessingContext<DocumentHistoryDTO> ctx = new ProcessingContext<>(dto, zipBytes);

        ProcessingException ex = assertThrows(ProcessingException.class, () -> {
            ZipDecompressor.decompress(ctx, tempDir.toString(), entryMapper).blockLast();
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
                .build();
        ProcessingContext<DocumentHistoryDTO> ctx = new ProcessingContext<>(dto, corruptBytes);

        StepVerifier.create(ZipDecompressor.decompress(ctx, tempDir.toString(), entryMapper))
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
                .businessDocumentId("doc-1")
                .build();
        ProcessingContext<DocumentHistoryDTO> ctx = new ProcessingContext<>(dto, zipBytes);

        StepVerifier.create(ZipDecompressor.decompress(ctx, null, entryMapper))
                .assertNext(extracted -> assertEquals("inner.txt", extracted.getHistory().getFilename()))
                .verifyComplete();

        StepVerifier.create(ZipDecompressor.decompress(ctx, "   ", entryMapper))
                .assertNext(extracted -> assertEquals("inner.txt", extracted.getHistory().getFilename()))
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