package com.kkodong.server.domain.enrollment.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PassEntryTypeConverter implements AttributeConverter<PassEntryType, String> {
    @Override
    public String convertToDatabaseColumn(PassEntryType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public PassEntryType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PassEntryType.valueOf(dbData.toUpperCase());
    }
}
