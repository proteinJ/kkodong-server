package com.kkodong.server.domain.note.domain;

import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 알림장(PN-14 작성, KG-10/11 보호자 열람). V9__add_partner_operations_domain.sql의
 * daily_notes에 매핑.
 *
 * <p><b>일괄 작성이어도 원생 단위로 개별 행이다.</b> "오늘 다 같이 산책했어요"를 20명에게
 * 보내더라도 행은 20개다 — 보호자마다 읽음 시각이 다르고, 한 명만 내용을 고치는 일이 반드시
 * 생기며, KG-14 통합 타임라인이 원생 단위로 조회되기 때문이다. 공유 본문 + 참조 구조로
 * 만들면 그 세 가지가 전부 특수 케이스가 된다.
 *
 * <p>⚠️ {@code DRAFT} 상태는 보호자에게 보이지 않는다. 발송(SENT) 이후에는 수정할 수 없다 —
 * 이미 읽었을 수 있는 내용을 조용히 바꾸면 "분명 이렇게 써 있었는데"가 된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "daily_notes")
public class DailyNote {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    /** 그날의 등원 기록. 결석일에도 알림장을 쓸 수 있어야 하므로 nullable이다. */
    @Column(name = "attendance_id")
    private UUID attendanceId;

    @Column(name = "note_date", nullable = false)
    private LocalDate noteDate;

    private String activity;
    private String meal;
    private String bathroom;

    // condition은 SQL 표준 비예약어라 컬럼명으로 쓸 수 있지만, 어느 방언에서 문제가 될지
    // 모르니 이름을 명시해 둔다.
    @Column(name = "condition")
    private String condition;

    private String remark;

    @Column(name = "author_user_id")
    private UUID authorUserId;

    @Column(nullable = false)
    @Builder.Default
    private NoteStatus status = NoteStatus.DRAFT; // NoteStatusConverter 자동 적용

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    /** 보호자가 연 시각(KG-10 읽음 표시). 견주 앱이 채운다. */
    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public NoteContent content() {
        return new NoteContent(activity, meal, bathroom, condition, remark);
    }

    /**
     * 내용 수정(PN-14). 발송 전에만 가능하다.
     *
     * <p>전달된 내용으로 <b>통째로 교체</b>한다 — 알림장 작성 화면은 다섯 항목을 한 번에
     * 보여주고 저장하므로 클라이언트가 항상 최종 상태를 보낸다. 부분 병합으로 만들면
     * "항목을 비우는" 동작을 표현할 방법이 따로 필요해진다.
     */
    public void edit(NoteContent content, UUID authorUserId) {
        requireDraft();
        this.activity = content.activity();
        this.meal = content.meal();
        this.bathroom = content.bathroom();
        this.condition = content.condition();
        this.remark = content.remark();
        this.authorUserId = authorUserId;
    }

    /**
     * 발송(PN-14). 이 시점부터 보호자에게 보인다.
     *
     * <p>⚠️ 빈 알림장은 보내지 않는다. 다섯 항목이 모두 비어 있으면 보호자에게는
     * "알림장이 왔는데 아무 내용이 없는" 상태가 되고, 그건 안 보낸 것보다 나쁘다.
     */
    public void send() {
        requireDraft();
        if (content().isEmpty()) {
            throw new BusinessException(ErrorCode.DAILY_NOTE_EMPTY);
        }
        this.status = NoteStatus.SENT;
        this.sentAt = OffsetDateTime.now();
    }

    /** 등원 기록과 잇는다. 예약 없이 온 워크인은 알림장을 먼저 쓸 수도 있어 나중에 붙인다. */
    public void linkAttendance(UUID attendanceId) {
        this.attendanceId = attendanceId;
    }

    public boolean isSent() {
        return status == NoteStatus.SENT;
    }

    private void requireDraft() {
        if (status == NoteStatus.SENT) {
            throw new BusinessException(ErrorCode.DAILY_NOTE_ALREADY_SENT);
        }
    }
}
