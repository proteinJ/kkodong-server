package com.kkodong.server.domain.reservation.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReservationStatusConverter implements AttributeConverter<ReservationStatus, String> {
    @Override
    public String convertToDatabaseColumn(ReservationStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public ReservationStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ReservationStatus.valueOf(dbData.toUpperCase());
    }
}
