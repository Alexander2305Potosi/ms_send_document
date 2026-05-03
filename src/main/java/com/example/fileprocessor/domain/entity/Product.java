package com.example.fileprocessor.domain.entity;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record Product(
    Long id,
    String productId,
    String name,
    LocalDateTime loadDate,
    String state,
    String messageError,
    java.util.List<ProductDocument> documents
) {}