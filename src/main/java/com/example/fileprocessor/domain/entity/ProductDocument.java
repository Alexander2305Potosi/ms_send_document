package com.example.fileprocessor.domain.entity;

import lombok.Builder;

@Builder
public record ProductDocument(
    String documentId,
    String filename,
    byte[] content,
    String contentType,
    long size,
    boolean isZip,
    String origin
) {}
