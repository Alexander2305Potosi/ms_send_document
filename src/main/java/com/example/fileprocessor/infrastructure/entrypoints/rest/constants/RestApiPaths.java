package com.example.fileprocessor.infrastructure.entrypoints.rest.constants;

/**
 * API path constants for REST endpoints.
 */
public final class RestApiPaths {

    private RestApiPaths() {}

    public static final String API_V1_PRODUCTS_LOAD = "/api/v1/products/load";
    public static final String API_V1_PRODUCTS = "/api/v1/products";
    public static final String API_V1_OPERATIONS_STATUS = "/api/v1/operations/{traceId}/status";
}