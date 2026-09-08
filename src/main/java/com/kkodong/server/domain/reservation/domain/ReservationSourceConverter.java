package com.kkodong.server.domain.reservation.domain;

import com.kkodong.server.global.common.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReservationSourceConverter extends LowercaseEnumConverter<ReservationSource> {
    public ReservationSourceConverter() {
        super(ReservationSource.class);
    }
}
