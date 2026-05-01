package com.example.fileprocessor.domain.service.rules;

import com.example.fileprocessor.domain.entity.ProductDocument;
import com.example.fileprocessor.domain.service.ValidationRule;

/**
 * Validates document size does not exceed configured limit.
 */
public record MaxSizeRule(long maxBytes) implements ValidationRule {

    @Override
    public boolean isValid(ProductDocument doc) {
        return doc.size() <= maxBytes;
    }

    @Override
    public String reasonIfInvalid(ProductDocument doc) {
        return "file size " + doc.size() + " exceeds limit of " + maxBytes + " bytes";
    }
}
