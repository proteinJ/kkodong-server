package com.kkodong.server.domain.dog.domain;

import com.kkodong.server.global.common.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DogSizeConverter extends LowercaseEnumConverter<DogSize> {
    public DogSizeConverter() {
        super(DogSize.class);
    }
}
