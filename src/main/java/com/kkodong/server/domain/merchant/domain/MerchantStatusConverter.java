package com.kkodong.server.domain.merchant.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MerchantStatusConverter implements AttributeConverter<MerchantStatus, String> {
    @Override
    public String convertToDatabaseColumn(MerchantStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public MerchantStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : MerchantStatus.valueOf(dbData.toUpperCase());
    }
}
