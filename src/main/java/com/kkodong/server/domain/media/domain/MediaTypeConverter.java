package com.kkodong.server.domain.media.domain;

import com.kkodong.server.global.common.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MediaTypeConverter extends LowercaseEnumConverter<MediaType> {
    public MediaTypeConverter() {
        super(MediaType.class);
    }
}
