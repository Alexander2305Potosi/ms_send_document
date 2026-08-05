package com.example.fileprocessor.domain.entity.product;

import lombok.Builder;
import lombok.Value;

/**
 * Transient context containing the audit history metadata and the binary file content.
 * Prevents memory leaks by keeping the byte[] out of the persistent history DTO.
 */
@Value
@Builder(toBuilder = true)
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PUBLIC)
public class ProcessingContext<H extends BaseDocumentHistoryDTO> {
    H history;
    byte[] fileContent;
}
