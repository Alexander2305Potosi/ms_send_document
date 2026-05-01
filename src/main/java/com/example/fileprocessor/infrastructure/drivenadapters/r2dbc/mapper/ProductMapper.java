package com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.mapper;

import com.example.fileprocessor.domain.entity.Product;
import com.example.fileprocessor.infrastructure.drivenadapters.r2dbc.entity.ProductEntity;

public class ProductMapper {

    public static ProductEntity toEntity(Product domain) {
        ProductEntity entity = new ProductEntity();
        entity.setProductId(domain.productId());
        entity.setName(domain.name());
        entity.setLoadDate(domain.loadDate());
        entity.setState(domain.state());
        entity.setMessageError(domain.messageError());
        return entity;
    }

    public static Product toDomain(ProductEntity entity) {
        return Product.builder()
            .productId(entity.getProductId())
            .name(entity.getName())
            .loadDate(entity.getLoadDate())
            .state(entity.getState())
            .messageError(entity.getMessageError())
            .build();
    }
}