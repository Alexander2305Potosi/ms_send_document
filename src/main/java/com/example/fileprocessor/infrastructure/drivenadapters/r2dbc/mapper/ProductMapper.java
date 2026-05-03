package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper;

import com.example.fileprocessor.domain.entity.Product;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.ProductEntity;

public class ProductMapper {

    public static ProductEntity toEntity(Product domain) {
        return ProductEntity.builder()
            .productId(domain.productId())
            .name(domain.name())
            .loadDate(domain.loadDate())
            .state(domain.state())
            .messageError(domain.messageError())
            .build();
    }

    public static Product toDomain(ProductEntity entity) {
        return Product.builder()
            .id(entity.getId())
            .productId(entity.getProductId())
            .name(entity.getName())
            .loadDate(entity.getLoadDate())
            .state(entity.getState())
            .messageError(entity.getMessageError())
            .build();
    }
}