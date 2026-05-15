package com.example.fileprocessor.domain.entity.homologation;

/**
 * Result of homologation resolution for a document.
 */
public record HomologationResult(
    String origin,
    String paisHomologado
) {}
