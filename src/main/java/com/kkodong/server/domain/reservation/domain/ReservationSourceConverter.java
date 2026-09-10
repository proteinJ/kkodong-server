package com.kkodong.server.domain.reservation.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReservationSourceConverter implements AttributeConverter<ReservationSource, String> {
    @Override
    public String convertToDatabaseColumn(ReservationSource attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public ReservationSource convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ReservationSource.valueOf(dbData.toUpperCase());
    }
}
