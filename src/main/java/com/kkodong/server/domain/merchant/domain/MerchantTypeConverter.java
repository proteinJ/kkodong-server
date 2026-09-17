package com.kkodong.server.domain.merchant.domain;

import com.kkodong.server.global.common.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MerchantTypeConverter extends LowercaseEnumConverter<MerchantType> {
    public MerchantTypeConverter() {
        super(MerchantType.class);
    }
}
