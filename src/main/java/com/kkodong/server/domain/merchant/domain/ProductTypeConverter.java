package com.kkodong.server.domain.merchant.domain;

import com.kkodong.server.global.common.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ProductTypeConverter extends LowercaseEnumConverter<ProductType> {
    public ProductTypeConverter() {
        super(ProductType.class);
    }
}
