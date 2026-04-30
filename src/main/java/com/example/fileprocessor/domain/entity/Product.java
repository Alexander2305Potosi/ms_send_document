package com.example.fileprocessor.domain.entity;

import lombok.Builder;

@Builder
public record Product(
    String productId,
    String name,
    java.util.List<ProductDocument> documents
) {}
