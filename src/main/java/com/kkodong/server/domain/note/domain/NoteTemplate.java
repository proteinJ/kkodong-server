package com.kkodong.server.domain.note.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 알림장 문구 템플릿(PN-14, FR-PN14-01).
 *
 * <p><b>이 기능의 성패가 여기 달려 있다.</b> 알림장은 매일 원생 수만큼 써야 하는 기능이라,
 * 입력 부담이 곧 이탈이다. 유치원 일과는 대체로 반복되므로("오늘도 실내 놀이터에서
 * 신나게 뛰어놀았어요") 자주 쓰는 문구를 저장해 두고 불러 쓰는 것이 실제 작성 시간을
 * 좌우한다.
 *
 * <p>템플릿은 매장 단위로 공유한다 — 선생님이 바뀌어도 매장의 말투가 이어져야 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "note_templates")
public class NoteTemplate {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    /** 목록에서 고를 때 보이는 이름. "평범한 하루", "컨디션 안 좋은 날" 등. */
    @Column(nullable = false)
    private String title;

    /** 항목별 기본값. {@link DailyNote}와 같은 다섯 항목이다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    @Builder.Default
    private NoteContent content = NoteContent.empty();

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public void update(String title, NoteContent content) {
        if (title != null) this.title = title;
        if (content != null) this.content = content;
    }
}
