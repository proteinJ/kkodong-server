package com.kkodong.server.domain.merchant.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ProductTypeConverter implements AttributeConverter<ProductType, String> {
    @Override
    public String convertToDatabaseColumn(ProductType attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public ProductType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ProductType.valueOf(dbData.toUpperCase());
    }
}
