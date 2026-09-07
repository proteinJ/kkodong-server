package com.kkodong.server.domain.enrollment.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EnrollmentStatusConverter implements AttributeConverter<EnrollmentStatus, String> {
    @Override
    public String convertToDatabaseColumn(EnrollmentStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public EnrollmentStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EnrollmentStatus.valueOf(dbData.toUpperCase());
    }
}
