package com.example.fileprocessor.domain.entity.homologation;

import lombok.Builder;

@Builder
public record HomologationCountry(
    String homologationFolder,
    String homologationCountry
) {}
