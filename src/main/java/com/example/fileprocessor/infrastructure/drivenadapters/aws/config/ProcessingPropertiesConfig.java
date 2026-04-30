package com.example.fileprocessor.infrastructure.drivenadapters.aws.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ProcessingProperties.class)
public class ProcessingPropertiesConfig {
}
