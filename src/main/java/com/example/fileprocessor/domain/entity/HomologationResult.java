package com.example.fileprocessor.domain.entity;

/**
 * Result of homologation resolution for a document.
 */
public record HomologationResult(
    String origin,
    String paisHomologado,
    boolean useV2
) {
    public HomologationResult(String origin, String paisHomologado) {
        this(origin, paisHomologado, false);
    }
}