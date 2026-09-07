package com.kkodong.server.domain.enrollment.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ApplicationStatusConverter implements AttributeConverter<ApplicationStatus, String> {
    @Override
    public String convertToDatabaseColumn(ApplicationStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public ApplicationStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ApplicationStatus.valueOf(dbData.toUpperCase());
    }
}
