package com.example.fileprocessor.domain.service;

import com.example.fileprocessor.domain.entity.ProductDocument;

/**
 * Contract for document validation rules.
 * Each rule evaluates a single aspect of the document.
 */
public interface ValidationRule {

    /**
     * Evaluates if the document passes this rule.
     */
    boolean isValid(ProductDocument doc);

    /**
     * Human-readable reason why validation failed.
     */
    String reasonIfInvalid(ProductDocument doc);
}
