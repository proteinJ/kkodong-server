package com.kkodong.server.domain.enrollment.domain;

import com.kkodong.server.global.common.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ApplicationStatusConverter extends LowercaseEnumConverter<ApplicationStatus> {
    public ApplicationStatusConverter() {
        super(ApplicationStatus.class);
    }
}
