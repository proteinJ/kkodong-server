package com.kkodong.server.domain.reservation.domain;

import com.kkodong.server.global.common.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReservationStatusConverter extends LowercaseEnumConverter<ReservationStatus> {
    public ReservationStatusConverter() {
        super(ReservationStatus.class);
    }
}
