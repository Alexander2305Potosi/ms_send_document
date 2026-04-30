package com.example.fileprocessor.domain.valueobject;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Value object encapsulating regex patterns for folder exclusion.
 * Compiles patterns at construction time for fail-fast validation.
 */
public class FolderExclusionRegexConfig {

    private final List<Pattern> exclusionPatterns;

    public FolderExclusionRegexConfig(List<String> regexPatterns) {
        if (regexPatterns == null || regexPatterns.isEmpty()) {
            this.exclusionPatterns = List.of();
        } else {
            this.exclusionPatterns = regexPatterns.stream()
                .map(this::compilePattern)
                .collect(Collectors.toUnmodifiableList());
        }
    }

    private Pattern compilePattern(String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new IllegalStateException(
                "Invalid folder exclusion regex pattern: '" + pattern + "'. " + e.getMessage(), e);
        }
    }

    /**
     * Evaluates if the origin path matches any exclusion pattern.
     * @param origin the document's origin path
     * @return true if the origin matches an exclusion pattern
     */
    public boolean shouldExclude(String origin) {
        if (origin == null || exclusionPatterns.isEmpty()) {
            return false;
        }
        return exclusionPatterns.stream()
            .anyMatch(pattern -> pattern.matcher(origin).find());
    }

    public boolean isEmpty() {
        return exclusionPatterns.isEmpty();
    }
}
