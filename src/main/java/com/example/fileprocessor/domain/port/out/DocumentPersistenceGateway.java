package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.product.Document;
import com.example.fileprocessor.domain.entity.product.DocumentHistoryDTO;

/**
 * Domain gateway orchestrating product document lifecycle and audit trail.
 * Inherits all database methods from the generic PersistenceGateway interface.
 */
public interface DocumentPersistenceGateway extends PersistenceGateway<Document, DocumentHistoryDTO> {
}
