package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.CommunicationLog;
import reactor.core.publisher.Mono;

/**
 * Port for persisting communication logs (SOAP, S3, etc.).
 * Used for audit trail and debugging.
 */
public interface CommunicationLogRepository {
    /**
     * Saves a communication log entry.
     * @param log the log entry to save
     * @return Mono that completes when saved
     */
    Mono<CommunicationLog> save(CommunicationLog log);
}
