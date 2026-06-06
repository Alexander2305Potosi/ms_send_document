package com.example.fileprocessor.domain.util;

import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.usecase.ProcessingResultCodes;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class for decompressing ZIP archives into individual DocumentHistoryDTO instances.
 */
public final class ZipDecompressor {

    private ZipDecompressor() {}

    public static Flux<DocumentHistoryDTO> decompress(DocumentHistoryDTO zipHistory) {
        if (!Boolean.TRUE.equals(zipHistory.getIsZip()) || zipHistory.getContent() == null) {
            return Flux.just(zipHistory);
        }

        byte[] content = zipHistory.getContent();
        if (content.length < 4 || content[0] != 0x50 || content[1] != 0x4B) {
            return Flux.error(new ProcessingException(
                "Failed to decompress ZIP '" + zipHistory.getFilename() + "': Not a valid ZIP file",
                ProcessingResultCodes.DECOMPRESSION_ERROR.name()));
        }

        try {
            List<DocumentHistoryDTO> entries;
            try {
                entries = decompressWithCharset(zipHistory, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException | IOException e) {
                // Fallback to ISO-8859-1 if UTF-8 fails (e.g. invalid bytes for entry name UTF-8 decoding)
                entries = decompressWithCharset(zipHistory, StandardCharsets.ISO_8859_1);
            }
            if (entries.isEmpty() && content[2] == 0x03 && content[3] == 0x04) {
                throw new IOException("ZIP file indicates entries but none could be parsed (corrupted)");
            }
            return Flux.fromIterable(entries);
        } catch (IOException | IllegalArgumentException e) {
            return Flux.error(new ProcessingException(
                "Failed to decompress ZIP '" + zipHistory.getFilename() + "': " + e.getMessage(),
                ProcessingResultCodes.DECOMPRESSION_ERROR.name(),
                e));
        }
    }

    private static List<DocumentHistoryDTO> decompressWithCharset(DocumentHistoryDTO zipHistory, Charset charset) throws IOException {
        List<DocumentHistoryDTO> entries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipHistory.getContent()), charset)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName() != null && !entry.getName().isBlank()) {
                    byte[] decompressed = zis.readAllBytes();
                    entries.add(buildHistoryEntry(entry.getName(), decompressed, zipHistory));
                }
                zis.closeEntry();
            }
        }
        return entries;
    }

    private static DocumentHistoryDTO buildHistoryEntry(String filename, byte[] content, DocumentHistoryDTO original) {
        return original.toBuilder()
            .businessDocumentId(original.getBusinessDocumentId() + "/" + filename)
            .filename(filename)
            .contentType(MimeTypeUtil.getMimeType(filename))
            .size((long) content.length)
            .isZip(false)
            .content(content)
            .build();
    }
}
