package com.example.fileprocessor.domain.service;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.PATTERN_MISMATCH;
import static com.example.fileprocessor.domain.usecase.ProcessingResultCodes.SIZE_EXCEEDED;

import com.example.fileprocessor.domain.entity.product.BaseDocumentHistoryDTO;
import com.example.fileprocessor.domain.exception.ProcessingException;
import com.example.fileprocessor.domain.port.out.RulesBussinesGateway;
import com.example.fileprocessor.infrastructure.config.ProcessorsProperties;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

/**
 * Default implementation of document validation gateway using generic BaseDocumentHistoryDTO.
 */
public class RulesBussinesService<H extends BaseDocumentHistoryDTO> implements RulesBussinesGateway<H> {

    private final Long maxFileSizeBytes;
    private final Pattern filenamePattern;

    public RulesBussinesService(ProcessorsProperties.ProcessorConfig config) {
        this.maxFileSizeBytes = (config.maxFileSizeBytes() != null && config.maxFileSizeBytes() > 0)
            ? config.maxFileSizeBytes()
            : null;
        this.filenamePattern = (config.filenamePattern() != null && !config.filenamePattern().isBlank())
            ? Pattern.compile(config.filenamePattern())
            : null;
    }

    @Override
    public Mono<H> validate(H history) {
        return validate(history, false);
    }

    @Override
    public Mono<H> validate(H history, boolean includeSizeCheck) {
        return Mono.defer(() -> {
            if (includeSizeCheck && maxFileSizeBytes != null && history.getSize() != null && history.getSize() > maxFileSizeBytes) {
                return Mono.error(new ProcessingException(
                    String.format("Size %,d bytes exceeds max %,d bytes for file '%s'",
                        history.getSize(), maxFileSizeBytes, history.getFilename()),
                    SIZE_EXCEEDED.name()));
            }
            if (filenamePattern != null && history.getFilename() != null && !filenamePattern.matcher(history.getFilename()).matches()) {
                return Mono.error(new ProcessingException(
                    String.format("Filename '%s' does not match pattern '%s'",
                        history.getFilename(), filenamePattern.pattern()),
                    PATTERN_MISMATCH.name()));
            }
            return Mono.just(history);
        });
    }
}
