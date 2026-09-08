package com.kkodong.server.domain.merchant.domain;

import com.kkodong.server.global.common.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StaffRoleConverter extends LowercaseEnumConverter<StaffRole> {
    public StaffRoleConverter() {
        super(StaffRole.class);
    }
}
