package com.kkodong.server.domain.note.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NoteStatusConverter implements AttributeConverter<NoteStatus, String> {
    @Override
    public String convertToDatabaseColumn(NoteStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public NoteStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : NoteStatus.valueOf(dbData.toUpperCase());
    }
}
