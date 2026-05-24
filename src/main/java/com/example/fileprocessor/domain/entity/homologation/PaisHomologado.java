package com.example.fileprocessor.domain.entity.homologation;

import com.fasterxml.jackson.databind.JsonNode;

public record PaisHomologado(Integer orden, JsonNode ruleNode, String homologationFolder, String homologationCountry) {}
