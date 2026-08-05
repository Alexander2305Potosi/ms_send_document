package com.example.fileprocessor.infrastructure.drivenadapters.restclient.dto;

import lombok.Builder;
import lombok.Value;
import java.util.List;

/**
 * DTO interno del Adapter REST para deserializar la respuesta del árbol de directorios.
 * No es entidad de dominio — la conversión a Document ocurre dentro del Adapter.
 */
@Value
@Builder
public class DirectoryNode {
    String id;
    String name;
    Integer source;
    String productId;
    String businessDocumentId;
    List<DirectoryNode> children;
}
