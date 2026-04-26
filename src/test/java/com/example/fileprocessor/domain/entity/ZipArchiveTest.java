package com.example.fileprocessor.domain.entity;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZipArchiveTest {

    @Test
    void extractDocuments_withValidZip_shouldExtractFiles() throws IOException {
        // Create a simple ZIP with one file
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos);

        java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry("test.txt");
        zos.putNextEntry(entry);
        zos.write("Hello World".getBytes());
        zos.closeEntry();
        zos.close();

        ZipArchive archive = ZipArchive.builder()
            .zipContent(baos.toByteArray())
            .originalFilename("test.zip")
            .build();

        List<ZipArchive.ExtractedDocument> docs = archive.extractDocuments();

        assertEquals(1, docs.size());
        assertEquals("test.txt", docs.get(0).getFilename());
        assertArrayEquals("Hello World".getBytes(), docs.get(0).getContent());
    }

    @Test
    void extractDocuments_withEmptyZip_shouldReturnEmptyList() throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos);
        zos.close();

        ZipArchive archive = ZipArchive.builder()
            .zipContent(baos.toByteArray())
            .originalFilename("empty.zip")
            .build();

        List<ZipArchive.ExtractedDocument> docs = archive.extractDocuments();

        assertTrue(docs.isEmpty());
    }

    @Test
    void extractDocuments_withDirectoryEntry_shouldSkip() throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos);

        // Add directory entry
        java.util.zip.ZipEntry dirEntry = new java.util.zip.ZipEntry("folder/");
        zos.putNextEntry(dirEntry);
        zos.closeEntry();

        // Add file entry
        java.util.zip.ZipEntry fileEntry = new java.util.zip.ZipEntry("folder/file.txt");
        zos.putNextEntry(fileEntry);
        zos.write("Content".getBytes());
        zos.closeEntry();
        zos.close();

        ZipArchive archive = ZipArchive.builder()
            .zipContent(baos.toByteArray())
            .originalFilename("withdir.zip")
            .build();

        List<ZipArchive.ExtractedDocument> docs = archive.extractDocuments();

        assertEquals(1, docs.size());
        assertEquals("file.txt", docs.get(0).getFilename());
    }

    @Test
    void extractDocuments_withHiddenFile_shouldSkip() throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos);

        // Add hidden file (starts with _)
        java.util.zip.ZipEntry hiddenEntry = new java.util.zip.ZipEntry("_hidden.txt");
        zos.putNextEntry(hiddenEntry);
        zos.write("Hidden content".getBytes());
        zos.closeEntry();

        // Add normal file
        java.util.zip.ZipEntry normalEntry = new java.util.zip.ZipEntry("visible.txt");
        zos.putNextEntry(normalEntry);
        zos.write("Visible content".getBytes());
        zos.closeEntry();
        zos.close();

        ZipArchive archive = ZipArchive.builder()
            .zipContent(baos.toByteArray())
            .originalFilename("withhidden.zip")
            .build();

        List<ZipArchive.ExtractedDocument> docs = archive.extractDocuments();

        assertEquals(1, docs.size());
        assertEquals("visible.txt", docs.get(0).getFilename());
    }
}