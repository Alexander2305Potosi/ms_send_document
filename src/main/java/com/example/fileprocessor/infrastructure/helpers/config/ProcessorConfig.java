package com.example.fileprocessor.infrastructure.helpers.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Processor configuration bound to application.yml under app.processors.
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
}
