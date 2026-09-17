package com.kkodong.server.domain.note.dto;

import com.kkodong.server.domain.note.domain.DailyNote;
import com.kkodong.server.domain.note.domain.NoteContent;
import com.kkodong.server.domain.note.domain.NoteStatus;
import com.kkodong.server.domain.note.domain.NoteTemplate;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class DailyNoteResponse {

    /** 알림장 한 건(PN-14, KG-11). */
    public record detailInfo(
            @Schema(description = "알림장 ID") UUID id,
            @Schema(description = "원생 ID") UUID enrollmentId,
            @Schema(description = "강아지 이름") String dogName,
            @Schema(description = "날짜") LocalDate noteDate,
            @Schema(description = "내용") NoteContent content,
            @Schema(description = "상태. DRAFT는 보호자에게 보이지 않는다") NoteStatus status,
            @Schema(description = "발송 시각") OffsetDateTime sentAt,
            @Schema(description = "보호자가 읽은 시각. null이면 미열람") OffsetDateTime readAt,
            @Schema(description = "작성자 유저 ID") UUID authorUserId
    ) {
        public static detailInfo of(DailyNote n, String dogName) {
            return new detailInfo(n.getId(), n.getEnrollmentId(), dogName, n.getNoteDate(),
                    n.content(), n.getStatus(), n.getSentAt(), n.getReadAt(), n.getAuthorUserId());
        }
    }

    /**
     * PN-14 작성 화면(하루치).
     *
     * <p><b>핵심은 {@code pending}이다</b> — 오늘 등원했는데 아직 알림장이 없는 원생 목록이다.
     * 이 목록이 비는 것이 하루 마감의 조건이며, 알림장 기능에서 점주가 실제로 보는 것은
     * "누구 걸 안 썼나"다.
     */
    public record workspace(
            @Schema(description = "날짜") LocalDate date,
            @Schema(description = "오늘 등원한 원생 수") int attendedCount,
            @Schema(description = "작성 완료(초안 포함) 수") int writtenCount,
            @Schema(description = "발송 완료 수") int sentCount,
            @Schema(description = "아직 알림장이 없는 원생 — 이 목록이 비면 마감이다")
            List<pendingItem> pending,
            @Schema(description = "이미 작성된 알림장(초안 + 발송)") List<detailInfo> notes
    ) {
        public record pendingItem(
                @Schema(description = "원생 ID") UUID enrollmentId,
                @Schema(description = "강아지 이름") String dogName
        ) {}
    }

    /**
     * 일괄 처리 결과.
     *
     * <p>부분 성공을 허용한다 — 20명에게 일괄 작성하는데 이미 발송된 한 명 때문에
     * 나머지 19명이 되돌아가면 점주는 원인을 모른 채 다시 눌러야 한다.
     */
    public record bulkResult(
            @Schema(description = "처리된 알림장 ID") List<UUID> succeeded,
            @Schema(description = "건너뛴 건과 사유") List<skipped> skipped
    ) {
        public record skipped(
                @Schema(description = "원생 또는 알림장 ID") UUID id,
                @Schema(description = "강아지 이름") String dogName,
                @Schema(description = "에러 코드", example = "DN002") String code,
                @Schema(description = "사유") String message
        ) {}
    }

    /** 템플릿(FR-PN14-01). */
    public record templateInfo(
            @Schema(description = "템플릿 ID") UUID id,
            @Schema(description = "이름") String title,
            @Schema(description = "항목별 기본값") NoteContent content,
            @Schema(description = "생성 시각") OffsetDateTime createdAt
    ) {
        public static templateInfo from(NoteTemplate t) {
            return new templateInfo(t.getId(), t.getTitle(), t.getContent(), t.getCreatedAt());
        }
    }
}
