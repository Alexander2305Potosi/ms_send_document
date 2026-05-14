package com.example.fileprocessor.domain.entity;

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
public class ProductHistory {
    private Long id;
    private String productId;
    private String name;
    private LocalDateTime loadDate;
    private String state;
    private String messageError;
}