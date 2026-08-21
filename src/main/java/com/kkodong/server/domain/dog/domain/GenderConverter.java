package com.kkodong.server.domain.dog.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * DB CHECK 제약(gender IN ('male','female'))이 소문자라, 자바 enum 관례(대문자)와
 * 자동으로 안 맞는다. 저장 시 소문자로, 조회 시 대문자로 변환.
 */
@Converter(autoApply = true)
public class GenderConverter implements AttributeConverter<Gender, String> {

    @Override
    public String convertToDatabaseColumn(Gender attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public Gender convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Gender.valueOf(dbData.toUpperCase());
    }
}
