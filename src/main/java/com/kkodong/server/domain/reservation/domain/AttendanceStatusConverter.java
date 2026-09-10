package com.kkodong.server.domain.reservation.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AttendanceStatusConverter implements AttributeConverter<AttendanceStatus, String> {
    @Override
    public String convertToDatabaseColumn(AttendanceStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public AttendanceStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AttendanceStatus.valueOf(dbData.toUpperCase());
    }
}
