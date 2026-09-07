package com.kkodong.server.domain.merchant.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StaffStatusConverter implements AttributeConverter<StaffStatus, String> {
    @Override
    public String convertToDatabaseColumn(StaffStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public StaffStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : StaffStatus.valueOf(dbData.toUpperCase());
    }
}
