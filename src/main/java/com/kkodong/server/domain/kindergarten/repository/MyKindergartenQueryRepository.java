package com.kkodong.server.domain.kindergarten.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 견주가 <b>다니는</b> 유치원을 읽는 전용 조회 레이어 (KG-09).
 *
 * <p><b>왜 JPA 엔티티를 만들지 않는가</b> — 여기서 읽는 테이블
 * ({@code enrollments}·{@code attendances}·{@code passes}·{@code daily_notes})은 전부
 * 점주 도메인 소유다. 견주 쪽에서 엔티티를 새로 선언하면 점주 PR(#71~#74)이 머지될 때
 * <b>같은 테이블에 매핑된 엔티티가 두 벌</b> 생긴다. 이 경로는 전부 읽기 전용이므로
 * 쓰기 모델을 복제할 이유가 없다 — 조회 모델만 따로 두는 편이 싸고 안전하다.
 * (같은 판단의 선례: {@code RecommendationCandidate} 가 dogs/users 를 네이티브 쿼리 +
 * 프로젝션으로 읽는다. 거기는 엔티티가 있어 Spring Data 프로젝션을 썼고, 여기는
 * 엔티티가 없어 {@code NamedParameterJdbcTemplate} 을 쓴다.)
 *
 * <p><b>권한은 SQL 안에 있다</b> — {@code dogs.owner_id = :userId} 조인이 모든 쿼리에
 * 들어간다. {@code enrollments.owner_user_id} 를 쓰지 않는 이유는 API_SPEC 13.1이
 * "권한은 {@code enrollment.dogId → dog.ownerId} 로 판정한다"로 계약을 고정했기 때문이다.
 * 서비스 계층에서 걸러내는 대신 조회 조건에 붙이면 "권한 검사를 빠뜨린 쿼리"가 생길 수 없다.
 */
@Repository
@RequiredArgsConstructor
public class MyKindergartenQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    // ── 조회 모델 (읽기 전용 행) ──────────────────────────────────────────
    // 표현(시각 포맷·D-day 계산·미리보기 자르기)은 서비스가 한다. 여기서는 DB 값 그대로 담는다.

    public record MyRow(UUID enrollmentId, UUID merchantId, String merchantName,
                        UUID dogId, String dogName, String dogImageUrl,
                        String status, int unreadNoteCount) {}

    public record EnrollmentRow(UUID enrollmentId, String merchantName, String dogName, String status) {}

    public record AttendanceRow(String status, OffsetDateTime checkedInAt, OffsetDateTime checkedOutAt) {}

    public record PassRow(UUID id, String productName, Integer remainingCount, Integer totalCount,
                          LocalDate expiresOn) {}

    public record NoteRow(UUID id, LocalDate noteDate,
                          String activity, String meal, String bathroom, String condition, String remark,
                          String thumbnailUrl, int photoCount, OffsetDateTime readAt) {}

    // ── 쿼리 ────────────────────────────────────────────────────────────

    /**
     * 마이 탭 유치원 섹션 (13.2). 다견 가구는 강아지 수만큼 행이 나온다.
     *
     * <p>{@code withdrawn} 은 서버가 뺀다 — 퇴원한 유치원이 마이 탭에 남아 있으면
     * 눌렀을 때 보여줄 게 없다.
     *
     * <p>{@code unreadNoteCount} 는 {@code status='sent' AND read_at IS NULL} 의 개수다.
     * 상관 서브쿼리 한 번으로 끝내 원생 수만큼 쿼리가 나가는 것을 막는다.
     */
    public List<MyRow> findMyEnrollments(UUID userId) {
        String sql = """
                SELECT e.id                 AS enrollment_id,
                       e.merchant_id        AS merchant_id,
                       m.name               AS merchant_name,
                       d.id                 AS dog_id,
                       d.name               AS dog_name,
                       d.profile_image_url  AS dog_image_url,
                       e.status             AS status,
                       (SELECT count(*)
                          FROM daily_notes n
                         WHERE n.enrollment_id = e.id
                           AND n.status = 'sent'
                           AND n.read_at IS NULL) AS unread_note_count
                  FROM enrollments e
                  JOIN merchants m ON m.id = e.merchant_id
                  JOIN dogs      d ON d.id = e.dog_id
                 WHERE d.owner_id = :userId
                   AND e.status <> 'withdrawn'
                 ORDER BY e.enrolled_on DESC, e.created_at DESC
                """;
        return jdbc.query(sql, Map.of("userId", userId), MY_ROW);
    }

    /**
     * 홈 헤더 + 권한 판정을 겸한다. 비어 있으면 "없거나 내 것이 아니다" 둘 중 하나이고,
     * 호출자는 그 둘을 구분하지 않는다 (서비스 주석 참조).
     */
    public Optional<EnrollmentRow> findMyEnrollment(UUID userId, UUID enrollmentId) {
        String sql = """
                SELECT e.id     AS enrollment_id,
                       m.name   AS merchant_name,
                       d.name   AS dog_name,
                       e.status AS status
                  FROM enrollments e
                  JOIN merchants m ON m.id = e.merchant_id
                  JOIN dogs      d ON d.id = e.dog_id
                 WHERE e.id = :enrollmentId
                   AND d.owner_id = :userId
                   AND e.status <> 'withdrawn'
                """;
        return jdbc.query(sql, Map.of("userId", userId, "enrollmentId", enrollmentId), ENROLLMENT_ROW)
                .stream().findFirst();
    }

    /**
     * 오늘의 등원. <b>행이 없으면 "오늘은 등원하지 않는 날"</b>이고, 응답에서 null이 된다.
     *
     * <p>{@code reverted_at IS NULL} 조건: 점주가 오처리를 되돌린 출석은 없던 일이다
     * (되돌리기는 행을 지우지 않고 표시만 남긴다 — 복원 이력이 매출 근거이기 때문).
     * 이걸 빼면 취소된 등원이 보호자 화면에 그대로 남는다.
     */
    public Optional<AttendanceRow> findTodayAttendance(UUID enrollmentId, LocalDate today) {
        String sql = """
                SELECT status, checked_in_at, checked_out_at
                  FROM attendances
                 WHERE enrollment_id = :enrollmentId
                   AND attendance_date = :today
                   AND reverted_at IS NULL
                 ORDER BY created_at DESC
                 LIMIT 1
                """;
        return jdbc.query(sql, Map.of("enrollmentId", enrollmentId, "today", today), ATTENDANCE_ROW)
                .stream().findFirst();
    }

    /**
     * 사용 가능한 이용권. {@code exhausted}·{@code expired}·{@code refunded}·{@code suspended}
     * 는 뺀다 — 홈 상단 카드는 "지금 쓸 수 있는 것"을 보여주는 자리다.
     * 지난 이용권 이력은 KG-13(이용권 상세)의 몫이다.
     *
     * <p>정렬은 만료 임박 순. 무기한({@code expires_on IS NULL})은 뒤로 보낸다.
     */
    public List<PassRow> findActivePasses(UUID enrollmentId) {
        String sql = """
                SELECT id,
                       product_name_snapshot AS product_name,
                       remaining_count,
                       total_count,
                       expires_on
                  FROM passes
                 WHERE enrollment_id = :enrollmentId
                   AND status = 'active'
                 ORDER BY expires_on ASC NULLS LAST, issued_at ASC
                """;
        return jdbc.query(sql, Map.of("enrollmentId", enrollmentId), PASS_ROW);
    }

    /**
     * 최근 알림장. <b>{@code status='sent'} 만</b> 내보낸다 — 쓰다 만 초안이 새면
     * 그 자체로 CS다("우리 아이만 내용이 없어요"). API_SPEC 13.1의 첫 번째 규칙이다.
     *
     * <p>⚠️ <b>사진 연결에 대한 주의</b>: {@code daily_notes} 에는 미디어 FK가 없다.
     * 스키마상 알림장과 사진을 이을 수 있는 유일한 경로가
     * {@code media_tags.enrollment_id} + {@code merchant_media.taken_on = note_date} 라
     * 그렇게 세고 있다. 점주가 촬영일을 다르게 넣으면 사진이 다른 날 알림장에 붙는다.
     * 정식으로는 {@code daily_notes ↔ merchant_media} 연결 테이블이 필요하다 — PR에 리뷰 포인트로 남겼다.
     */
    public List<NoteRow> findRecentNotes(UUID enrollmentId, int limit) {
        String sql = """
                SELECT n.id,
                       n.note_date,
                       n.activity, n.meal, n.bathroom, n.condition, n.remark,
                       n.read_at,
                       (SELECT count(*)
                          FROM media_tags mt
                          JOIN merchant_media mm ON mm.id = mt.media_id
                         WHERE mt.enrollment_id = n.enrollment_id
                           AND mm.taken_on = n.note_date) AS photo_count,
                       (SELECT mm.thumbnail_url
                          FROM media_tags mt
                          JOIN merchant_media mm ON mm.id = mt.media_id
                         WHERE mt.enrollment_id = n.enrollment_id
                           AND mm.taken_on = n.note_date
                         ORDER BY mm.created_at ASC
                         LIMIT 1) AS thumbnail_url
                  FROM daily_notes n
                 WHERE n.enrollment_id = :enrollmentId
                   AND n.status = 'sent'
                 ORDER BY n.note_date DESC
                 LIMIT :limit
                """;
        return jdbc.query(sql, Map.of("enrollmentId", enrollmentId, "limit", limit), NOTE_ROW);
    }

    // ── RowMapper ───────────────────────────────────────────────────────
    // 람다를 필드로 뽑아 둔다. 쿼리마다 인라인으로 쓰면 KG-10/11 에서 같은 매핑을 다시 쓴다.

    private static final RowMapper<MyRow> MY_ROW = (rs, i) -> new MyRow(
            rs.getObject("enrollment_id", UUID.class),
            rs.getObject("merchant_id", UUID.class),
            rs.getString("merchant_name"),
            rs.getObject("dog_id", UUID.class),
            rs.getString("dog_name"),
            rs.getString("dog_image_url"),
            rs.getString("status"),
            rs.getInt("unread_note_count")
    );

    private static final RowMapper<EnrollmentRow> ENROLLMENT_ROW = (rs, i) -> new EnrollmentRow(
            rs.getObject("enrollment_id", UUID.class),
            rs.getString("merchant_name"),
            rs.getString("dog_name"),
            rs.getString("status")
    );

    private static final RowMapper<AttendanceRow> ATTENDANCE_ROW = (rs, i) -> new AttendanceRow(
            rs.getString("status"),
            rs.getObject("checked_in_at", OffsetDateTime.class),
            rs.getObject("checked_out_at", OffsetDateTime.class)
    );

    /** {@code getInt} 는 NULL을 0으로 바꾸므로 기간권 구분이 사라진다. 반드시 {@code getObject} 로 읽는다. */
    private static final RowMapper<PassRow> PASS_ROW = (rs, i) -> new PassRow(
            rs.getObject("id", UUID.class),
            rs.getString("product_name"),
            rs.getObject("remaining_count", Integer.class),
            rs.getObject("total_count", Integer.class),
            rs.getObject("expires_on", LocalDate.class)
    );

    private static final RowMapper<NoteRow> NOTE_ROW = (rs, i) -> new NoteRow(
            rs.getObject("id", UUID.class),
            rs.getObject("note_date", LocalDate.class),
            rs.getString("activity"),
            rs.getString("meal"),
            rs.getString("bathroom"),
            rs.getString("condition"),
            rs.getString("remark"),
            rs.getString("thumbnail_url"),
            rs.getInt("photo_count"),
            rs.getObject("read_at", OffsetDateTime.class)
    );
}
