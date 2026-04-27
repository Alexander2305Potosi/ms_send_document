package com.example.fileprocessor.infrastructure.drivenadapters.aws.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;

import java.net.URI;

@org.springframework.context.annotation.Profile("s3")
@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class AwsConfig {

    @Bean
    public S3AsyncClient s3AsyncClient(S3Properties s3Properties) {
        S3AsyncClientBuilder builder = S3AsyncClient.builder()
            .region(Region.of(s3Properties.region()));

        if (s3Properties.accessKey() != null && !s3Properties.accessKey().isBlank()
            && s3Properties.secretKey() != null && !s3Properties.secretKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(s3Properties.accessKey(), s3Properties.secretKey())
            ));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        if (s3Properties.endpoint() != null && !s3Properties.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3Properties.endpoint()));
        }

        if (s3Properties.pathStyleAccess()) {
            builder.serviceConfiguration(config -> config.pathStyleAccessEnabled(true));
        }

        return builder.build();
    }
}
