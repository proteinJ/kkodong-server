package com.kkodong.server.domain.safety.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReportReasonConverter implements AttributeConverter<ReportReason, String> {
    @Override
    public String convertToDatabaseColumn(ReportReason attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public ReportReason convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ReportReason.valueOf(dbData.toUpperCase());
    }
}
