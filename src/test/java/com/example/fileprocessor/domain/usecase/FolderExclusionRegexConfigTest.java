package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.valueobject.FolderExclusionRegexConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FolderExclusionRegexConfigTest {

    @Test
    void shouldExclude_shouldReturnTrue_whenOriginMatchesPattern() {
        var config = new FolderExclusionRegexConfig(List.of(".*/backup/.*", "temp/.*", "archive/202[0-2]/.*"));

        assertThat(config.shouldExclude("/data/folder/backup/file.pdf")).isTrue();
        assertThat(config.shouldExclude("/tmp/uploads/temp/file.pdf")).isTrue();
        assertThat(config.shouldExclude("/archive/2021/documents/file.pdf")).isTrue();
        assertThat(config.shouldExclude("/archive/2022/reports/file.pdf")).isTrue();
    }

    @Test
    void shouldExclude_shouldReturnFalse_whenOriginDoesNotMatch() {
        var config = new FolderExclusionRegexConfig(List.of(".*/backup/.*", "temp/.*"));

        assertThat(config.shouldExclude("/data/normal_folder/file.pdf")).isFalse();
        assertThat(config.shouldExclude("/archive/2023/file.pdf")).isFalse();
        assertThat(config.shouldExclude("/documents/file.pdf")).isFalse();
    }

    @Test
    void shouldExclude_shouldReturnFalse_whenConfigIsEmpty() {
        var config = new FolderExclusionRegexConfig(List.of());

        assertThat(config.shouldExclude("/any/path")).isFalse();
    }

    @Test
    void shouldExclude_shouldReturnFalse_whenOriginIsNull() {
        var config = new FolderExclusionRegexConfig(List.of(".*_backup$"));

        assertThat(config.shouldExclude(null)).isFalse();
    }

    @Test
    void shouldExclude_shouldThrowIllegalState_whenPatternIsInvalid() {
        assertThatThrownBy(() -> new FolderExclusionRegexConfig(List.of("[invalid(regex")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid folder exclusion regex pattern");
    }

    @Test
    void isEmpty_shouldReturnTrue_whenNoPatterns() {
        var emptyConfig = new FolderExclusionRegexConfig(List.of());
        var configWithPatterns = new FolderExclusionRegexConfig(List.of(".*"));

        assertThat(emptyConfig.isEmpty()).isTrue();
        assertThat(configWithPatterns.isEmpty()).isFalse();
    }
}
