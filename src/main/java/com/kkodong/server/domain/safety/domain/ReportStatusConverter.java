package com.kkodong.server.domain.safety.domain;

import com.kkodong.server.global.common.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ReportStatusConverter extends LowercaseEnumConverter<ReportStatus> {
    public ReportStatusConverter() {
        super(ReportStatus.class);
    }
}
