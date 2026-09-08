package com.kkodong.server.domain.merchant.domain;

import com.kkodong.server.global.common.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StaffStatusConverter extends LowercaseEnumConverter<StaffStatus> {
    public StaffStatusConverter() {
        super(StaffStatus.class);
    }
}
