package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.port.in.FileValidationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DocumentValidationRulesTest {

    @Mock
    private FileValidationConfig config;

    private DocumentValidationRules rules;

    @BeforeEach
    void setUp() {
        rules = new DocumentValidationRules(config);
    }

    @Test
    void shouldSkipFolder_withTmpOrigin_shouldReturnTrue() {
        lenient().when(config.foldersToSkip()).thenReturn(List.of("/tmp", "/transient"));

        assertTrue(rules.shouldSkipFolder("/tmp/doc.pdf"));
    }

    @Test
    void shouldSkipFolder_withNormalOrigin_shouldReturnFalse() {
        lenient().when(config.foldersToSkip()).thenReturn(List.of("/tmp", "/transient"));

        assertFalse(rules.shouldSkipFolder("incoming/docs/file.pdf"));
    }

    @Test
    void shouldSkipFolder_withNullOrigin_shouldReturnFalse() {
        assertFalse(rules.shouldSkipFolder(null));
    }

    @Test
    void shouldSendByOrigin_withMatchingPattern_shouldReturnTrue() {
        lenient().when(config.originPatternsToSend()).thenReturn(List.of("incoming", "docs"));

        assertTrue(rules.shouldSendByOrigin("incoming/file.pdf"));
    }

    @Test
    void shouldSendByOrigin_withNoMatchingPattern_shouldReturnFalse() {
        lenient().when(config.originPatternsToSend()).thenReturn(List.of("incoming", "docs"));

        assertFalse(rules.shouldSendByOrigin("other/folder/file.pdf"));
    }

    @Test
    void shouldSendByOrigin_withEmptyPatterns_shouldReturnTrue() {
        lenient().when(config.originPatternsToSend()).thenReturn(List.of());

        assertTrue(rules.shouldSendByOrigin("any/path/file.pdf"));
    }

    @Test
    void shouldNotSendBySize_withLargeFile_shouldReturnTrue() {
        lenient().when(config.maxFileSizeMb()).thenReturn(50);

        assertTrue(rules.shouldNotSendBySize(50 * 1024 * 1024));
    }

    @Test
    void shouldNotSendBySize_withSmallFile_shouldReturnFalse() {
        lenient().when(config.maxFileSizeMb()).thenReturn(50);

        assertFalse(rules.shouldNotSendBySize(10 * 1024 * 1024));
    }

    @Test
    void extractFolderInfo_withNullKeywords_shouldReturnDefaultFolder() {
        lenient().when(config.keywords()).thenReturn(null);

        var info = rules.extractFolderInfo("incoming/test/myfile.pdf");

        assertNotNull(info);
        assertEquals(".", info.parentFolder());
    }
}