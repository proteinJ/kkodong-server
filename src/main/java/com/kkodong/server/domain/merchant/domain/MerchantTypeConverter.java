package com.kkodong.server.domain.merchant.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MerchantTypeConverter implements AttributeConverter<MerchantType, String> {
    @Override
    public String convertToDatabaseColumn(MerchantType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public MerchantType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : MerchantType.valueOf(dbData.toUpperCase());
    }
}
