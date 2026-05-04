package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper;

import com.example.fileprocessor.domain.entity.ProductHistory;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.ProductEntity;

public class ProductMapper {

    public static ProductEntity toEntity(ProductHistory domain) {
        return ProductEntity.builder()
            .productId(domain.productId())
            .name(domain.name())
            .loadDate(domain.loadDate())
            .state(domain.state())
            .messageError(domain.messageError())
            .build();
    }

    public static ProductHistory toDomain(ProductEntity entity) {
        return ProductHistory.builder()
            .id(entity.getId())
            .productId(entity.getProductId())
            .name(entity.getName())
            .loadDate(entity.getLoadDate())
            .state(entity.getState())
            .messageError(entity.getMessageError())
            .build();
    }
}