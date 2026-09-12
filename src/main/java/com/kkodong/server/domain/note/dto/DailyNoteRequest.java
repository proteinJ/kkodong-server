package com.kkodong.server.domain.note.dto;

import com.kkodong.server.domain.note.domain.NoteContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class DailyNoteRequest {

    /** 개별 작성·수정(PN-14). 발송 전 초안에만 쓸 수 있다. */
    public record write(
            @NotNull(message = "원생 ID는 필수입니다.")
            @Schema(description = "대상 원생 ID", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID enrollmentId,

            @NotNull(message = "날짜는 필수입니다.")
            @Schema(description = "알림장 날짜", example = "2026-09-08",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate noteDate,

            @Schema(description = "불러올 템플릿 ID. 지정하면 템플릿 위에 아래 content를 덮어쓴다")
            UUID templateId,

            @Schema(description = "알림장 내용. templateId와 함께 보내면 비어 있지 않은 항목만 템플릿을 덮는다")
            NoteContent content
    ) {}

    /**
     * 일괄 작성(PN-14). 여러 원생에게 같은 내용으로 초안을 만든다.
     *
     * <p>⚠️ 만들어지는 것은 원생 수만큼의 <b>개별 알림장</b>이다. 이후 한 명만 따로 고칠 수 있다.
     */
    public record writeBulk(
            @NotEmpty(message = "대상을 하나 이상 선택해야 합니다.")
            @Size(max = 100, message = "한 번에 100명까지 처리할 수 있습니다.")
            @Schema(description = "대상 원생 ID 목록", requiredMode = Schema.RequiredMode.REQUIRED)
            List<UUID> enrollmentIds,

            @NotNull(message = "날짜는 필수입니다.")
            @Schema(description = "알림장 날짜", requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate noteDate,

            @Schema(description = "불러올 템플릿 ID") UUID templateId,
            @Schema(description = "알림장 내용") NoteContent content
    ) {}

    /** 발송(PN-14). 여러 건을 한 번에 보낸다. */
    public record send(
            @NotEmpty(message = "발송할 알림장을 하나 이상 선택해야 합니다.")
            @Size(max = 100, message = "한 번에 100건까지 발송할 수 있습니다.")
            @Schema(description = "발송할 알림장 ID 목록", requiredMode = Schema.RequiredMode.REQUIRED)
            List<UUID> noteIds
    ) {}

    /** 템플릿 저장(FR-PN14-01). */
    public record saveTemplate(
            @NotBlank(message = "템플릿 이름은 필수입니다.")
            @Size(max = 100, message = "템플릿 이름은 100자를 넘을 수 없습니다.")
            @Schema(description = "템플릿 이름", example = "평범한 하루",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String title,

            @NotNull(message = "템플릿 내용은 필수입니다.")
            @Schema(description = "항목별 기본값", requiredMode = Schema.RequiredMode.REQUIRED)
            NoteContent content
    ) {}
}
