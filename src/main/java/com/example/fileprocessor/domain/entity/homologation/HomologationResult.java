package com.example.fileprocessor.domain.entity.homologation;

import lombok.Builder;

/**
 * Result of homologation resolution for a document.
 */
@Builder
public record HomologationResult(
    String categoriaDocument,
    HomologationCountry homologationCountry
) {}
