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
@Table(name = "historico_documentos")
public class DocumentHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "use_case", nullable = false)
    private String useCase;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "retry", nullable = false)
    private Integer retry = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}