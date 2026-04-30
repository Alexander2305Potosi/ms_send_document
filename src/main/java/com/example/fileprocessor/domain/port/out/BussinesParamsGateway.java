package com.example.fileprocessor.domain.port.out;

import com.example.fileprocessor.infrastructure.drivenadapters.aws.config.BussinesParams;

/**
 * Port for retrieving Vale configuration.
 */
public interface BussinesParamsGateway {
    String getValue(BussinesParams bussinesParams);
}