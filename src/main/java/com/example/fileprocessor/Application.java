package com.example.fileprocessor;

import com.example.fileprocessor.infrastructure.entrypoints.rest.config.AnimalRestProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.config.DocumentRestProperties;
import com.example.fileprocessor.infrastructure.entrypoints.rest.config.PathProperties;
import com.example.fileprocessor.infrastructure.helpers.soap.config.SoapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {
    WebMvcAutoConfiguration.class,
    org.springframework.boot.autoconfigure.data.r2dbc.R2dbcRepositoriesAutoConfiguration.class
})
@EnableConfigurationProperties({SoapProperties.class, DocumentRestProperties.class, PathProperties.class, AnimalRestProperties.class})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}