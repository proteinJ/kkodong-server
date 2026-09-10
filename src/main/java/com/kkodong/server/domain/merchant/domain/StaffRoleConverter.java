package com.kkodong.server.domain.merchant.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StaffRoleConverter implements AttributeConverter<StaffRole, String> {
    @Override
    public String convertToDatabaseColumn(StaffRole attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public StaffRole convertToEntityAttribute(String dbData) {
        return dbData == null ? null : StaffRole.valueOf(dbData.toUpperCase());
    }
}
