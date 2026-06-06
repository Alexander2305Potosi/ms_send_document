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

    // Magic numbers for Zip format checks (SonarQube compliance)
    private static final byte ZIP_SIG_1 = 0x50; // 'P'
    private static final byte ZIP_SIG_2 = 0x4B; // 'K'
    
    private static final byte LFH_SIG_3 = 0x03;
    private static final byte LFH_SIG_4 = 0x04;
    
    private static final byte CDFH_SIG_3 = 0x01;
    private static final byte CDFH_SIG_4 = 0x02;
    
    private static final byte EOCD_SIG_3 = 0x05;
    private static final byte EOCD_SIG_4 = 0x06;

    private static final int MIN_ZIP_HEADER_LEN = 4;
    private static final int MIN_EOCD_LEN = 22;

    // LFH Constants
    private static final int LFH_MIN_LEN = 30;
    private static final int LFH_FLAG_OFFSET = 6;
    private static final int LFH_CRC_OFFSET = 14;
    private static final int LFH_COMPRESSED_SIZE_OFFSET = 18;
    private static final int LFH_UNCOMPRESSED_SIZE_OFFSET = 22;

    // CDFH Constants
    private static final int CDFH_MIN_LEN = 46;
    private static final int CDFH_FLAG_OFFSET = 8;
    private static final int CDFH_METHOD_OFFSET = 10;
    private static final int CDFH_CRC_OFFSET = 16;
    private static final int CDFH_COMPRESSED_SIZE_OFFSET = 20;
    private static final int CDFH_UNCOMPRESSED_SIZE_OFFSET = 24;
    private static final int CDFH_FILENAME_LEN_OFFSET = 28;
    private static final int CDFH_EXTRA_LEN_OFFSET = 30;
    private static final int CDFH_COMMENT_LEN_OFFSET = 32;
    private static final int CDFH_LOCAL_HEADER_OFFSET_OFFSET = 42;

    // EOCD Constants
    private static final int EOCD_ENTRIES_OFFSET = 10;
    private static final int EOCD_CD_SIZE_OFFSET = 12;
    private static final int EOCD_CD_OFFSET_OFFSET = 16;

    // Bitmasks and special values
    private static final int GP_FLAG_DATA_DESCRIPTOR_MASK = 8;
    private static final int COMPRESSION_METHOD_STORED = 0;
    private static final long MASK_32_BIT = 0xFFFFFFFFL;
    private static final int BYTE_MASK = 0xFF;
    private static final int SHIFT_8 = 8;
    private static final int SHIFT_16 = 16;
    private static final int SHIFT_24 = 24;

    private ZipDecompressor() {}

    public static Flux<DocumentHistoryDTO> decompress(DocumentHistoryDTO zipHistory) {
        if (!Boolean.TRUE.equals(zipHistory.getIsZip()) || zipHistory.getContent() == null) {
            return Flux.just(zipHistory);
        }

        byte[] content = patchZipIfNecessary(zipHistory.getContent().clone());
        if (content.length < MIN_ZIP_HEADER_LEN || content[0] != ZIP_SIG_1 || content[1] != ZIP_SIG_2) {
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
            if (entries.isEmpty() && content[2] == LFH_SIG_3 && content[3] == LFH_SIG_4) {
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
            for (int i = content.length - MIN_EOCD_LEN; i >= 0; i--) {
                if (content[i] == ZIP_SIG_1 && content[i+1] == ZIP_SIG_2 &&
                    content[i+2] == EOCD_SIG_3 && content[i+3] == EOCD_SIG_4) {
                    eocdOffset = i;
                    break;
                }
            }
            if (eocdOffset == -1) {
                return content;
            }

            int totalEntries = readUnsignedShort(content, eocdOffset + EOCD_ENTRIES_OFFSET);
            int cdSize = readInt(content, eocdOffset + EOCD_CD_SIZE_OFFSET);
            int cdOffset = readInt(content, eocdOffset + EOCD_CD_OFFSET_OFFSET);

            int currentOffset = cdOffset;
            for (int entry = 0; entry < totalEntries; entry++) {
                if (currentOffset + CDFH_MIN_LEN > content.length) {
                    break;
                }
                if (content[currentOffset] != ZIP_SIG_1 || content[currentOffset+1] != ZIP_SIG_2 ||
                    content[currentOffset+2] != CDFH_SIG_3 || content[currentOffset+3] != CDFH_SIG_4) {
                    break;
                }

                int flag = readUnsignedShort(content, currentOffset + CDFH_FLAG_OFFSET);
                int method = readUnsignedShort(content, currentOffset + CDFH_METHOD_OFFSET);
                long crc = readInt(content, currentOffset + CDFH_CRC_OFFSET) & MASK_32_BIT;
                long compressedSize = readInt(content, currentOffset + CDFH_COMPRESSED_SIZE_OFFSET) & MASK_32_BIT;
                long uncompressedSize = readInt(content, currentOffset + CDFH_UNCOMPRESSED_SIZE_OFFSET) & MASK_32_BIT;
                int fileNameLen = readUnsignedShort(content, currentOffset + CDFH_FILENAME_LEN_OFFSET);
                int extraFieldLen = readUnsignedShort(content, currentOffset + CDFH_EXTRA_LEN_OFFSET);
                int commentLen = readUnsignedShort(content, currentOffset + CDFH_COMMENT_LEN_OFFSET);
                long localHeaderOffset = readInt(content, currentOffset + CDFH_LOCAL_HEADER_OFFSET_OFFSET) & MASK_32_BIT;

                if (method == COMPRESSION_METHOD_STORED && (flag & GP_FLAG_DATA_DESCRIPTOR_MASK) != 0) {
                    int lfh = (int) localHeaderOffset;
                    if (lfh + LFH_MIN_LEN <= content.length) {
                        if (content[lfh] == ZIP_SIG_1 && content[lfh+1] == ZIP_SIG_2 &&
                            content[lfh+2] == LFH_SIG_3 && content[lfh+3] == LFH_SIG_4) {
                            
                            int localFlag = readUnsignedShort(content, lfh + LFH_FLAG_OFFSET);
                            localFlag &= ~GP_FLAG_DATA_DESCRIPTOR_MASK;
                            writeUnsignedShort(content, lfh + LFH_FLAG_OFFSET, localFlag);

                            writeInt(content, lfh + LFH_CRC_OFFSET, (int) crc);
                            writeInt(content, lfh + LFH_COMPRESSED_SIZE_OFFSET, (int) compressedSize);
                            writeInt(content, lfh + LFH_UNCOMPRESSED_SIZE_OFFSET, (int) uncompressedSize);
                        }
                    }
                }

                currentOffset += CDFH_MIN_LEN + fileNameLen + extraFieldLen + commentLen;
            }
        } catch (Exception e) {
            // Ignore patching errors and let ZipInputStream handle it
        }
        return content;
    }

    private static int readUnsignedShort(byte[] data, int offset) {
        return (data[offset] & BYTE_MASK) | ((data[offset + 1] & BYTE_MASK) << SHIFT_8);
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & BYTE_MASK) |
               ((data[offset + 1] & BYTE_MASK) << SHIFT_8) |
               ((data[offset + 2] & BYTE_MASK) << SHIFT_16) |
               ((data[offset + 3] & BYTE_MASK) << SHIFT_24);
    }

    private static void writeUnsignedShort(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & BYTE_MASK);
        data[offset + 1] = (byte) ((value >> SHIFT_8) & BYTE_MASK);
    }

    private static void writeInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & BYTE_MASK);
        data[offset + 1] = (byte) ((value >> SHIFT_8) & BYTE_MASK);
        data[offset + 2] = (byte) ((value >> SHIFT_16) & BYTE_MASK);
        data[offset + 3] = (byte) ((value >> SHIFT_24) & BYTE_MASK);
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
