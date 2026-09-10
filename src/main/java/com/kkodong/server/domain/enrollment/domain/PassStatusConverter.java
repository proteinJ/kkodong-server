package com.kkodong.server.domain.enrollment.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PassStatusConverter implements AttributeConverter<PassStatus, String> {
    @Override
    public String convertToDatabaseColumn(PassStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public PassStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PassStatus.valueOf(dbData.toUpperCase());
    }
}
