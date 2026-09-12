package com.kkodong.server.domain.reservation.domain;

import com.kkodong.server.global.common.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AttendanceStatusConverter extends LowercaseEnumConverter<AttendanceStatus> {
    public AttendanceStatusConverter() {
        super(AttendanceStatus.class);
    }
}
