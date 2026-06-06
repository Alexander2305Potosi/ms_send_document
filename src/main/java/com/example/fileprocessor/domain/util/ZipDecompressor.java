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

        byte[] content = patchZipIfNecessary(zipHistory.getContent().clone());
        if (content.length < 4 || content[0] != 0x50 || content[1] != 0x4B) {
            return Flux.error(new ProcessingException(
                "Failed to decompress ZIP '" + zipHistory.getFilename() + "': Not a valid ZIP file",
                ProcessingResultCodes.DECOMPRESSION_ERROR.name()));
        }

        try {
            List<DocumentHistoryDTO> entries;
            try {
                entries = decompressWithCharset(zipHistory, content, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException | IOException e) {
                // Fallback to ISO-8859-1 if UTF-8 fails (e.g. invalid bytes for entry name UTF-8 decoding)
                entries = decompressWithCharset(zipHistory, content, StandardCharsets.ISO_8859_1);
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

    private static List<DocumentHistoryDTO> decompressWithCharset(DocumentHistoryDTO zipHistory, byte[] content, Charset charset) throws IOException {
        List<DocumentHistoryDTO> entries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(content), charset)) {
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

    private static byte[] patchZipIfNecessary(byte[] content) {
        try {
            int eocdOffset = -1;
            for (int i = content.length - 22; i >= 0; i--) {
                if (content[i] == 0x50 && content[i+1] == 0x4B && content[i+2] == 0x05 && content[i+3] == 0x06) {
                    eocdOffset = i;
                    break;
                }
            }
            if (eocdOffset == -1) {
                return content;
            }

            int totalEntries = readUnsignedShort(content, eocdOffset + 10);
            int cdSize = readInt(content, eocdOffset + 12);
            int cdOffset = readInt(content, eocdOffset + 16);

            int currentOffset = cdOffset;
            for (int entry = 0; entry < totalEntries; entry++) {
                if (currentOffset + 46 > content.length) {
                    break;
                }
                if (content[currentOffset] != 0x50 || content[currentOffset+1] != 0x4B ||
                    content[currentOffset+2] != 0x01 || content[currentOffset+3] != 0x02) {
                    break;
                }

                int flag = readUnsignedShort(content, currentOffset + 8);
                int method = readUnsignedShort(content, currentOffset + 10);
                long crc = readInt(content, currentOffset + 16) & 0xFFFFFFFFL;
                long compressedSize = readInt(content, currentOffset + 20) & 0xFFFFFFFFL;
                long uncompressedSize = readInt(content, currentOffset + 24) & 0xFFFFFFFFL;
                int fileNameLen = readUnsignedShort(content, currentOffset + 28);
                int extraFieldLen = readUnsignedShort(content, currentOffset + 30);
                int commentLen = readUnsignedShort(content, currentOffset + 32);
                long localHeaderOffset = readInt(content, currentOffset + 42) & 0xFFFFFFFFL;

                if (method == 0 && (flag & 8) != 0) {
                    int lfh = (int) localHeaderOffset;
                    if (lfh + 30 <= content.length) {
                        if (content[lfh] == 0x50 && content[lfh+1] == 0x4B &&
                            content[lfh+2] == 0x03 && content[lfh+3] == 0x04) {
                            
                            int localFlag = readUnsignedShort(content, lfh + 6);
                            localFlag &= ~8;
                            writeUnsignedShort(content, lfh + 6, localFlag);

                            writeInt(content, lfh + 14, (int) crc);
                            writeInt(content, lfh + 18, (int) compressedSize);
                            writeInt(content, lfh + 22, (int) uncompressedSize);
                        }
                    }
                }

                currentOffset += 46 + fileNameLen + extraFieldLen + commentLen;
            }
        } catch (Exception e) {
            // Ignore patching errors and let ZipInputStream handle it
        }
        return content;
    }

    private static int readUnsignedShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF) |
               ((data[offset + 1] & 0xFF) << 8) |
               ((data[offset + 2] & 0xFF) << 16) |
               ((data[offset + 3] & 0xFF) << 24);
    }

    private static void writeUnsignedShort(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private static void writeInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >> 24) & 0xFF);
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
