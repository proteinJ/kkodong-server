package com.kkodong.server.domain.dog.domain;

import com.kkodong.server.global.common.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EnergyLevelConverter extends LowercaseEnumConverter<EnergyLevel> {
    public EnergyLevelConverter() {
        super(EnergyLevel.class);
    }
}
