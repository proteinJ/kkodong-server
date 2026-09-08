package com.kkodong.server.domain.enrollment.domain;

import com.kkodong.server.global.common.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PassEntryTypeConverter extends LowercaseEnumConverter<PassEntryType> {
    public PassEntryTypeConverter() {
        super(PassEntryType.class);
    }
}
