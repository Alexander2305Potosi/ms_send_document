package com.example.fileprocessor.infrastructure.drivenadapters.aws.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BussinesParams {
    REGEX("regex"),
    MAX_FILE_SIZE("maxFileSize");

    private final String key;
}
