package com.pawwalk.server.domain.dog.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DogSizeConverter implements AttributeConverter<DogSize, String> {

    @Override
    public String convertToDatabaseColumn(DogSize attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public DogSize convertToEntityAttribute(String dbData) {
        return dbData == null ? null : DogSize.valueOf(dbData.toUpperCase());
    }
}
