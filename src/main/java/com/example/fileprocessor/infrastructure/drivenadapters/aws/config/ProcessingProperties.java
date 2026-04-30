package com.example.fileprocessor.infrastructure.drivenadapters.aws.config;

import com.example.fileprocessor.domain.port.out.BussinesParamsGateway;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "params")
public record ProcessingProperties(
        Map<String, String> namesmap
) implements BussinesParamsGateway {

    @Override
    public String getValue(BussinesParams bussinesParams) {
        return namesmap.get(bussinesParams.getKey());
    }
}
