package com.example.fileprocessor.infrastructure.aws.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
@RequiredArgsConstructor
public class AwsConfig {

    private final S3Properties s3Properties;

    @Bean
    public S3AsyncClient s3AsyncClient() {
        var builder = S3AsyncClient.builder()
            .region(Region.of(s3Properties.region()))
            .credentialsProvider(DefaultCredentialsProvider.create());

        if (s3Properties.endpoint() != null && !s3Properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3Properties.endpoint()));
        }

        return builder.build();
    }
}