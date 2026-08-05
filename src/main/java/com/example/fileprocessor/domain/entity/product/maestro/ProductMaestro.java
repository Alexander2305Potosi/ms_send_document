package com.example.fileprocessor.domain.entity.product.maestro;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductMaestro {
    private Long id;
    private String productId;
    private String name;
    private LocalDateTime loadDate;
    private String state;
    private String originFolder;
    private String originCountry;
}