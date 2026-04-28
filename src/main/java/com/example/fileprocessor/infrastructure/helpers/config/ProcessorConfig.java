package com.example.fileprocessor.infrastructure.helpers.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Unified processor configuration bound to application.yml.
 *
 * <pre>
 * app:
 *   processors:
 *     soap:
 *       max-size: 10485760
 *       allowed-types: pdf,txt,csv
 *       max-file-size-mb: 50
 *     s3:
 *       max-size: 52428800
 *       allowed-types: pdf,txt,csv,zip
 *       max-file-size-mb: 100
 * </pre>
 */
@Validated
@ConfigurationProperties(prefix = "app.processors")
public class ProcessorConfig {

    @NotNull
    private ProcessorSettings soap;

    @NotNull
    private ProcessorSettings s3;

    public ProcessorSettings getSoap() { return soap; }
    public void setSoap(ProcessorSettings soap) { this.soap = soap; }

    public ProcessorSettings getS3() { return s3; }
    public void setS3(ProcessorSettings s3) { this.s3 = s3; }

    /**
     * Returns the settings for the given processor type.
     * @param processorType "s3" or anything else (defaults to SOAP)
     */
    public ProcessorSettings forProcessor(String processorType) {
        return "s3".equalsIgnoreCase(processorType) ? s3 : soap;
    }
}
