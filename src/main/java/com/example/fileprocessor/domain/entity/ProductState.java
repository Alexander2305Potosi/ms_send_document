package com.example.fileprocessor.domain.entity;

/**
 * Product state constants.
 */
public final class ProductState {
    public static final String PENDING = "PENDING";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String PROCESSED = "PROCESSED";
    public static final String FAILED = "FAILED";
    public static final String SYNCED = "SYNCED";

    private ProductState() {}
}
