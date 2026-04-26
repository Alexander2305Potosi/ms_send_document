package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.SoapCommunicationLog;
import reactor.core.publisher.Mono;

/**
 * Port for persisting SOAP communication logs.
 * Used for audit trail and debugging.
 */
public interface SoapCommunicationLogRepository {
    /**
     * Saves a communication log entry.
     * @param log the log entry to save
     * @return Mono that completes when saved
     */
    Mono<SoapCommunicationLog> save(SoapCommunicationLog log);
}
