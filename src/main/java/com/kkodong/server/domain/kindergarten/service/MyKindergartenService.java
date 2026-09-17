package com.kkodong.server.domain.kindergarten.service;

import com.kkodong.server.domain.kindergarten.dto.MyKindergartenResponse;
import com.kkodong.server.domain.kindergarten.repository.MyKindergartenQueryRepository;
import com.kkodong.server.domain.kindergarten.repository.MyKindergartenQueryRepository.AttendanceRow;
import com.kkodong.server.domain.kindergarten.repository.MyKindergartenQueryRepository.EnrollmentRow;
import com.kkodong.server.domain.kindergarten.repository.MyKindergartenQueryRepository.NoteRow;
import com.kkodong.server.domain.kindergarten.repository.MyKindergartenQueryRepository.PassRow;
import com.kkodong.server.global.error.BusinessException;
import com.kkodong.server.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 마이 유치원 홈 (KG-09).
 *
 * <p>조회는 전부 {@link MyKindergartenQueryRepository} 가 하고, 이 클래스는 <b>표현</b>만 맡는다 —
 * 시각을 매장 시간대로 자르고, D-day를 세고, 미리보기를 잘라 계약 모양으로 만든다.
 * 계약(API_SPEC 13.2/13.3)이 "서버가 완성해서 내려준다"로 정해 둔 것들이라 클라이언트로 미루지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class MyKindergartenService {

    /**
     * 매장 시간대. {@code merchants} 에 시간대 컬럼이 없어 국내 단일 시간대로 고정한다.
     * 해외 매장이 생기면 매장별 컬럼으로 옮겨야 한다 — 그때 바꿀 자리를 한 곳으로 모아 둔다.
     */
    private static final ZoneId MERCHANT_ZONE = ZoneId.of("Asia/Seoul");

    /** 계약 13.1: 시각은 {@code "09:12"} 처럼 매장 시간대로 잘라서 보낸다. */
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /** 홈에 띄우는 최근 알림장 건수 (FR-C09-03). */
    private static final int RECENT_NOTE_LIMIT = 3;

    /**
     * 미리보기 길이. <b>서버가 자르는</b> 이유는 계약 13.4에 있다 —
     * 클라이언트가 자르면 기기 폭에 따라 아이템마다 길이가 달라진다.
     */
    private static final int PREVIEW_MAX_LENGTH = 40;

    private final MyKindergartenQueryRepository queryRepository;

    /**
     * "오늘"의 출처. 오늘의 등원 조회와 만료 D-day가 전부 여기 걸려 있어,
     * {@code LocalDate.now()} 를 직접 부르면 <b>자정 근처에서만 깨지는 테스트</b>가 된다.
     */
    private final Clock clock;

    /**
     * 운영용.
     *
     * <p><b>{@code @Autowired} 를 빼면 안 된다.</b> 생성자가 하나면 Spring 이 그것을 쓰지만,
     * 둘 이상이면 어느 것을 쓸지 알려주지 않는 한 <b>기본 생성자</b>를 찾다가
     * {@code NoSuchMethodException} 으로 기동이 깨진다. 아래 테스트용 생성자가 있으므로
     * 이 표시가 필요하다.
     */
    @Autowired
    public MyKindergartenService(MyKindergartenQueryRepository queryRepository) {
        this(queryRepository, Clock.system(MERCHANT_ZONE));
    }

    /** 테스트용 — "오늘"을 고정한다. package-private이라 밖에서는 보이지 않는다. */
    MyKindergartenService(MyKindergartenQueryRepository queryRepository, Clock clock) {
        this.queryRepository = queryRepository;
        this.clock = clock;
    }

    /**
     * 마이 탭의 유치원 섹션 (13.2).
     *
     * <p>재원 중인 곳이 없으면 빈 배열이다 — 예외가 아니다. 유치원을 안 다니는 것이
     * 정상 상태이고, 이 API는 마이 탭이 열릴 때마다 호출된다.
     */
    public List<MyKindergartenResponse.myItem> getMyKindergartens(UUID userId) {
        return queryRepository.findMyEnrollments(userId).stream()
                .map(row -> new MyKindergartenResponse.myItem(
                        row.enrollmentId(),
                        row.merchantId(),
                        row.merchantName(),
                        row.dogId(),
                        row.dogName(),
                        row.dogImageUrl(),
                        row.status(),
                        row.unreadNoteCount()
                ))
                .toList();
    }

    /**
     * 마이 유치원 홈 (13.3).
     *
     * <p><b>없는 것과 남의 것을 구분하지 않고 둘 다 404</b>로 답한다. 403을 주면
     * "그 enrollmentId 는 존재한다"가 드러나는데, 계약 13.1이 지적했듯
     * {@code enrollmentId} 는 점주 화면에도 뜨는 값이라 아는 것만으로는 권한이 되지 않는다.
     * 존재 여부를 흘리지 않는 쪽이 맞다.
     */
    public MyKindergartenResponse.home getHome(UUID userId, UUID enrollmentId) {
        EnrollmentRow enrollment = queryRepository.findMyEnrollment(userId, enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MY_ENROLLMENT_NOT_FOUND));

        LocalDate today = LocalDate.now(clock);

        MyKindergartenResponse.todayAttendance attendance =
                queryRepository.findTodayAttendance(enrollmentId, today)
                        .map(this::toAttendance)
                        .orElse(null);   // null = "오늘은 등원하지 않는 날" (계약 13.3)

        List<MyKindergartenResponse.pass> passes = queryRepository.findActivePasses(enrollmentId).stream()
                .map(row -> toPass(row, today))
                .toList();

        List<MyKindergartenResponse.notePreview> recentNotes =
                queryRepository.findRecentNotes(enrollmentId, RECENT_NOTE_LIMIT).stream()
                        .map(this::toNotePreview)
                        .toList();

        return new MyKindergartenResponse.home(
                enrollment.enrollmentId(),
                enrollment.merchantName(),
                enrollment.dogName(),
                enrollment.status(),
                attendance,
                passes,
                recentNotes
        );
    }

    // ── 표현 변환 ────────────────────────────────────────────────────────

    private MyKindergartenResponse.todayAttendance toAttendance(AttendanceRow row) {
        return new MyKindergartenResponse.todayAttendance(
                row.status(),
                toClockTime(row.checkedInAt()),
                toClockTime(row.checkedOutAt())
        );
    }

    private MyKindergartenResponse.pass toPass(PassRow row, LocalDate today) {
        return new MyKindergartenResponse.pass(
                row.id(),
                row.productName(),
                row.remainingCount(),   // 기간권이면 null 그대로 — 0으로 바꾸면 소진과 구분이 안 된다
                row.totalCount(),
                row.expiresOn() == null ? null : row.expiresOn().toString(),
                daysUntilExpiry(row.expiresOn(), today)
        );
    }

    private MyKindergartenResponse.notePreview toNotePreview(NoteRow row) {
        return new MyKindergartenResponse.notePreview(
                row.id(),
                row.noteDate().toString(),
                buildPreview(row),
                row.thumbnailUrl(),
                row.photoCount(),
                row.readAt() == null ? null : row.readAt().toInstant().toString()
        );
    }

    /**
     * 만료까지 남은 일수. 무기한이면 null, 이미 지났으면 음수다 (계약 13.3).
     * 음수를 0으로 깎지 않는 이유: iOS가 "만료됨"과 "오늘 만료"를 갈라 그린다.
     */
    private Integer daysUntilExpiry(LocalDate expiresOn, LocalDate today) {
        if (expiresOn == null) {
            return null;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(today, expiresOn));
    }

    /**
     * {@code TIMESTAMPTZ} 를 매장 시간대의 {@code HH:mm} 으로 자른다.
     * 타임스탬프를 그대로 내려주고 클라이언트가 포맷하게 두지 않는 이유는 계약 13.1에 있다.
     */
    private String toClockTime(OffsetDateTime at) {
        if (at == null) {
            return null;
        }
        return at.atZoneSameInstant(MERCHANT_ZONE).format(HH_MM);
    }

    /**
     * 미리보기 문장. {@code activity} 가 비어 있으면 다음 항목으로 넘어간다 (계약 13.4).
     *
     * <p>다섯 칸이 전부 비어 있을 수도 있다 — 사진만 올리고 글을 안 쓴 알림장이 그렇다.
     * 그때는 null을 주고 iOS가 사진만 그리게 둔다. 빈 문자열이나 "내용 없음"을 만들지 않는다.
     */
    private String buildPreview(NoteRow row) {
        return Stream.of(row.activity(), row.meal(), row.bathroom(), row.condition(), row.remark())
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .map(this::truncate)
                .orElse(null);
    }

    private String truncate(String text) {
        String trimmed = text.strip();
        return trimmed.length() <= PREVIEW_MAX_LENGTH
                ? trimmed
                : trimmed.substring(0, PREVIEW_MAX_LENGTH) + "…";
    }
}
