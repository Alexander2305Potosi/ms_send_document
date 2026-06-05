package com.example.fileprocessor.infrastructure.helpers.soap.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

/**
 * Creates the {@link WebClient} bean used by {@link com.example.fileprocessor.infrastructure.drivenadapters.soap.SoapGatewayAdapter}.
 * The client is pre-configured with the SOAP base URL and connection/read timeouts
 * derived from {@link SoapProperties#timeoutSeconds()}.
 */
@Configuration
public class SoapConfig {

    @Bean
    public WebClient soapWebClient(SoapProperties properties) {
        int timeoutMs = properties.timeoutSeconds() * 1000;

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(properties.timeoutSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(properties.timeoutSeconds(), TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(properties.endpoint())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
