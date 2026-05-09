package com.example.fileprocessor.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    private Long id;
    private String documentId;
    private String productId;
    private Boolean active;
    private String docKey;
    private String name;
    private String owner;
    private String path;
    private String state;
    private String versionContract;
    private String errorMessage;
    private boolean isZip;
    private String parentZipName;
    private String useCase;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer retryCount;

    public int getRetryCountSafe() {
        return retryCount != null ? retryCount : 0;
    }
}
