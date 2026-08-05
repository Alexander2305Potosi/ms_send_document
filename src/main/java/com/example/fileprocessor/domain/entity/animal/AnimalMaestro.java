package com.example.fileprocessor.domain.entity.animal;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AnimalMaestro {
    Long id;
    String name;
    String category;
}
