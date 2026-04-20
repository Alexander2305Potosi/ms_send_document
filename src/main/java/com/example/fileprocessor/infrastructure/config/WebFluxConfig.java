package com.example.fileprocessor.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        // Aumentar tamaño máximo de buffer para multipart
        configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024); // 10MB
    }
}
