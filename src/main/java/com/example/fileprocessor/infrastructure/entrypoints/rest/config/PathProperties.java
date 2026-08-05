package com.example.fileprocessor.infrastructure.entrypoints.rest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "app.paths")
public record PathProperties(
        @NotBlank String basePath,

        @NotBlank String API_V1_PRODUCTS,

        @NotBlank String API_V1_PRODUCTS_SYNC,

        @NotBlank String API_V1_PRODUCTS_SYNC_STATUS,

        @NotBlank String API_V1_PRODUCTS_PROCESS_STATUS,
        
        @NotBlank String API_V1_PRODUCTS_DAILY_ANIMAL,
        
        @NotBlank String API_V1_PRODUCTS_PROCESS_STATUS_DAILY) {
    public PathProperties {
        if (basePath == null) {
            basePath = "/api/v1";
        }
        if (API_V1_PRODUCTS == null) {
            API_V1_PRODUCTS = "/products/{type_job}";
        }
        if (API_V1_PRODUCTS_SYNC == null) {
            API_V1_PRODUCTS_SYNC = "/products/sync/{type_job}";
        }
        if (API_V1_PRODUCTS_SYNC_STATUS == null) {
            API_V1_PRODUCTS_SYNC_STATUS = "/products/sync/status/{type_job}";
        }
        if (API_V1_PRODUCTS_PROCESS_STATUS == null) {
            API_V1_PRODUCTS_PROCESS_STATUS = "/products/process/status/{type_job}";
        }
        if (API_V1_PRODUCTS_DAILY_ANIMAL == null) {
            API_V1_PRODUCTS_DAILY_ANIMAL = "/products/daily/animal";
        }
        if (API_V1_PRODUCTS_PROCESS_STATUS_DAILY == null) {
            API_V1_PRODUCTS_PROCESS_STATUS_DAILY = "/products/process/status/daily/animal";
        }
    }
}
