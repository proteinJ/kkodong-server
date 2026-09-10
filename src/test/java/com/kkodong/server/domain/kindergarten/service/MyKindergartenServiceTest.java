package com.kkodong.server.domain.kindergarten.service;

import com.kkodong.server.domain.kindergarten.dto.MyKindergartenResponse;
import com.kkodong.server.domain.kindergarten.repository.MyKindergartenQueryRepository;
import com.kkodong.server.domain.kindergarten.repository.MyKindergartenQueryRepository.AttendanceRow;
import com.kkodong.server.domain.kindergarten.repository.MyKindergartenQueryRepository.EnrollmentRow;
import com.kkodong.server.domain.kindergarten.repository.MyKindergartenQueryRepository.NoteRow;
import com.kkodong.server.domain.kindergarten.repository.MyKindergartenQueryRepository.PassRow;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 마이 유치원 홈의 <b>표현 규칙</b> 테스트 (KG-09).
 *
 * <p>SQL은 실제 DB로만 검증되므로 여기서 다루지 않는다. 대신 API_SPEC 13.3이
 * "이렇게 안 하면 화면이 잘못 그려진다"고 못박은 규칙들만 고정한다 — 이 규칙들은
 * 무심코 리팩터링하면 조용히 깨지고, 깨져도 컴파일은 통과한다.
 *
 * <p>"오늘"을 {@link Clock} 으로 고정한 이유: D-day 계산이 자정 근처에서만 흔들리는
 * 테스트가 되지 않게 하기 위해서다.
 */
class MyKindergartenServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);
    private static final Clock FIXED = Clock.fixed(
            TODAY.atTime(14, 0).atZone(SEOUL).toInstant(), SEOUL);

    private final MyKindergartenQueryRepository repository =
            Mockito.mock(MyKindergartenQueryRepository.class);
    private final MyKindergartenService service = new MyKindergartenService(repository, FIXED);

    private final UUID userId = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();

    private void givenEnrollment() {
        Mockito.when(repository.findMyEnrollment(userId, enrollmentId))
                .thenReturn(Optional.of(new EnrollmentRow(enrollmentId, "댕댕유치원 성수점", "초코", "active")));
    }

    @Test
    @DisplayName("없거나 내 것이 아닌 등록은 404다 — 403을 주면 그 ID가 존재한다는 사실이 드러난다")
    void notMineIsNotFound() {
        Mockito.when(repository.findMyEnrollment(userId, enrollmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHome(userId, enrollmentId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MY_ENROLLMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("오늘 등원 기록이 없으면 todayAttendance는 null — '등원 전(scheduled)'과 다른 상태다")
    void noAttendanceRowMeansNotAttendingToday() {
        givenEnrollment();
        Mockito.when(repository.findTodayAttendance(enrollmentId, TODAY)).thenReturn(Optional.empty());

        MyKindergartenResponse.home result = service.getHome(userId, enrollmentId);

        assertThat(result.todayAttendance()).isNull();
    }

    @Test
    @DisplayName("등·하원 시각은 매장 시간대의 HH:mm으로 잘라서 내려간다")
    void checkInTimeIsClippedToMerchantZone() {
        givenEnrollment();
        // UTC 00:12 == 서울 09:12. 서버가 어느 시간대에서 돌든 09:12가 나와야 한다.
        Mockito.when(repository.findTodayAttendance(enrollmentId, TODAY)).thenReturn(Optional.of(
                new AttendanceRow("attended",
                        OffsetDateTime.of(2026, 9, 9, 0, 12, 0, 0, ZoneOffset.UTC),
                        null)));

        MyKindergartenResponse.todayAttendance attendance = service.getHome(userId, enrollmentId).todayAttendance();

        assertThat(attendance.status()).isEqualTo("attended");
        assertThat(attendance.checkedInAt()).isEqualTo("09:12");
        // 하원은 상태가 아니라 checkedOutAt으로 판단한다 — 아직 원에 있으면 null이다
        assertThat(attendance.checkedOutAt()).isNull();
    }

    @Test
    @DisplayName("기간권은 remainingCount·totalCount가 null이다 — 0으로 채우면 소진된 횟수권과 구분되지 않는다")
    void periodPassKeepsNullCounts() {
        givenEnrollment();
        Mockito.when(repository.findActivePasses(enrollmentId)).thenReturn(List.of(
                new PassRow(UUID.randomUUID(), "1개월 정기권", null, null, LocalDate.of(2026, 9, 30))));

        MyKindergartenResponse.pass pass = service.getHome(userId, enrollmentId).passes().get(0);

        assertThat(pass.remainingCount()).isNull();
        assertThat(pass.totalCount()).isNull();
        assertThat(pass.expiresOn()).isEqualTo("2026-09-30");
        assertThat(pass.daysUntilExpiry()).isEqualTo(21);
    }

    @Test
    @DisplayName("만료가 지난 이용권의 daysUntilExpiry는 음수다 — 0으로 깎으면 '오늘 만료'와 같아진다")
    void expiredPassKeepsNegativeDays() {
        givenEnrollment();
        Mockito.when(repository.findActivePasses(enrollmentId)).thenReturn(List.of(
                new PassRow(UUID.randomUUID(), "10회권", 2, 10, TODAY.minusDays(3))));

        assertThat(service.getHome(userId, enrollmentId).passes().get(0).daysUntilExpiry()).isEqualTo(-3);
    }

    @Test
    @DisplayName("만료일이 없는(무기한) 이용권은 expiresOn·daysUntilExpiry가 모두 null이다")
    void openEndedPassHasNoExpiry() {
        givenEnrollment();
        Mockito.when(repository.findActivePasses(enrollmentId)).thenReturn(List.of(
                new PassRow(UUID.randomUUID(), "무기한권", 5, 10, null)));

        MyKindergartenResponse.pass pass = service.getHome(userId, enrollmentId).passes().get(0);

        assertThat(pass.expiresOn()).isNull();
        assertThat(pass.daysUntilExpiry()).isNull();
    }

    @Test
    @DisplayName("preview는 activity가 비면 다음 항목으로 넘어간다")
    void previewFallsThroughEmptyFields() {
        givenEnrollment();
        Mockito.when(repository.findRecentNotes(Mockito.eq(enrollmentId), Mockito.anyInt())).thenReturn(List.of(
                note(null, "  ", "대변 1회", "좋음", null)));

        assertThat(firstNote().preview()).isEqualTo("대변 1회");
    }

    @Test
    @DisplayName("다섯 칸이 전부 비면 preview는 null이다 — 빈 문자열이나 '내용 없음'을 만들지 않는다")
    void previewIsNullWhenNothingWritten() {
        givenEnrollment();
        Mockito.when(repository.findRecentNotes(Mockito.eq(enrollmentId), Mockito.anyInt())).thenReturn(List.of(
                note(null, null, null, null, null)));

        assertThat(firstNote().preview()).isNull();
    }

    @Test
    @DisplayName("긴 본문은 서버가 자른다 — 클라이언트가 자르면 기기마다 길이가 달라진다")
    void longPreviewIsTruncatedByServer() {
        givenEnrollment();
        String long100 = "가".repeat(100);
        Mockito.when(repository.findRecentNotes(Mockito.eq(enrollmentId), Mockito.anyInt())).thenReturn(List.of(
                note(long100, null, null, null, null)));

        String preview = firstNote().preview();

        assertThat(preview).hasSize(41).endsWith("…");   // 40자 + 말줄임표
    }

    @Test
    @DisplayName("홈은 최근 알림장을 3건만 요청한다 (FR-C09-03)")
    void homeAsksForThreeNotes() {
        givenEnrollment();

        service.getHome(userId, enrollmentId);

        Mockito.verify(repository).findRecentNotes(enrollmentId, 3);
    }

    private MyKindergartenResponse.notePreview firstNote() {
        return service.getHome(userId, enrollmentId).recentNotes().get(0);
    }

    private NoteRow note(String activity, String meal, String bathroom, String condition, String remark) {
        return new NoteRow(UUID.randomUUID(), TODAY, activity, meal, bathroom, condition, remark,
                null, 0, null);
    }
}
