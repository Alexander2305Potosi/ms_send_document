package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "documento")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_document", nullable = false)
    private String documentId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Builder.Default
    @Column(name = "active")
    private Boolean active = true;

    @Column(name = "doc_key")
    private String docKey;

    @Column(name = "name")
    private String name;

    @Column(name = "owner")
    private String owner;

    @Column(name = "path")
    private String path;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "version_contract")
    private String versionContract;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "error_message")
    private String errorMessage;

    @Builder.Default
    @Column(name = "is_zip")
    private Boolean isZip = false;

    @Column(name = "parent_zip_name")
    private String parentZipName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}