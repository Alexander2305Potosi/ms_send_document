package com.example.fileprocessor;

import com.example.fileprocessor.infrastructure.config.FileUploadProperties;
import com.example.fileprocessor.infrastructure.rest.config.DocumentRestProperties;
import com.example.fileprocessor.infrastructure.soap.config.SoapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = {WebMvcAutoConfiguration.class})
@EnableConfigurationProperties({FileUploadProperties.class, SoapProperties.class, DocumentRestProperties.class})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}