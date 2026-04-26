package com.example.fileprocessor.config;

import com.example.fileprocessor.infrastructure.helpers.config.FileUploadProperties;
import com.example.fileprocessor.infrastructure.drivenadapters.soap.config.SoapProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@TestConfiguration
@EnableConfigurationProperties({FileUploadProperties.class, SoapProperties.class})
public class TestConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
