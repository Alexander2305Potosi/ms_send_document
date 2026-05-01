package com.example.fileprocessor.domain.service.rules;

import com.example.fileprocessor.domain.entity.ProductDocument;
import com.example.fileprocessor.domain.service.ValidationRule;

import java.util.regex.Pattern;

/**
 * Validates filename matches a regex pattern.
 */
public record FilenamePatternRule(String pattern) implements ValidationRule {

    @Override
    public boolean isValid(ProductDocument doc) {
        return Pattern.matches(pattern, doc.filename());
    }

    @Override
    public String reasonIfInvalid(ProductDocument doc) {
        return "filename '" + doc.filename() + "' does not match pattern '" + pattern + "'";
    }
}
