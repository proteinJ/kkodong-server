package com.kkodong.server.domain.safety.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TargetTypeConverter implements AttributeConverter<TargetType, String> {

    @Override
    public String convertToDatabaseColumn(TargetType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public TargetType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : TargetType.valueOf(dbData.toUpperCase());
    }
}
