package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.entity.ProductDocumentToProcess;

/**
 * Result of a successful claim. Contains both the claim metadata and the document.
 */
public record ClaimedDocument(
    ProductDocumentToProcess document,
    ClaimResult claim
) {}
