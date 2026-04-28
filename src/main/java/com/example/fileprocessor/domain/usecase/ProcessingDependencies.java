package com.example.fileprocessor.domain.usecase;

import com.example.fileprocessor.domain.port.out.CommunicationLogRepository;
import com.example.fileprocessor.domain.port.out.FileGateway;
import com.example.fileprocessor.domain.port.out.ProductDocumentRepository;

/**
 * Groups all shared dependencies for document processing use cases.
 * Reduces constructor parameters by aggregating related dependencies.
 */
public record ProcessingDependencies(
    ProductDocumentRepository documentRepository,
    ProductStatusAggregator statusAggregator,
    FileGateway fileGateway,
    CommunicationLogRepository logRepository
) {}
