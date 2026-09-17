package com.kkodong.server.domain.note.domain;

import com.kkodong.server.global.common.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NoteStatusConverter extends LowercaseEnumConverter<NoteStatus> {
    public NoteStatusConverter() {
        super(NoteStatus.class);
    }
}
