package com.kkodong.server.domain.safety.domain;

import com.kkodong.server.global.common.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReportReasonConverter extends LowercaseEnumConverter<ReportReason> {
    public ReportReasonConverter() {
        super(ReportReason.class);
    }
}
