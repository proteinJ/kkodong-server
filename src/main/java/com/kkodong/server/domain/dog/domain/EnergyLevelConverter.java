package com.kkodong.server.domain.dog.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EnergyLevelConverter implements AttributeConverter<EnergyLevel, String> {

    @Override
    public String convertToDatabaseColumn(EnergyLevel attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public EnergyLevel convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EnergyLevel.valueOf(dbData.toUpperCase());
    }
}
