package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.domain.entity.SoapCommunicationLog;
import reactor.core.publisher.Mono;

public interface SoapCommunicationLogRepository {
    Mono<SoapCommunicationLog> save(SoapCommunicationLog log);
}
