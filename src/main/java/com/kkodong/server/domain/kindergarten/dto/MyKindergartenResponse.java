package com.kkodong.server.domain.kindergarten.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * 견주가 <b>다니는</b> 유치원을 읽는 응답 (KG-09).
 *
 * <p>필드 모양은 {@code docs/design/API_SPEC.md} 13.2 / 13.3 계약을 그대로 따른다.
 * iOS(`Features/Kindergarten/`)가 이 계약대로 이미 구현돼 있어, 이름이 어긋나면
 * 화면이 통째로 빈다. 바꿔야 하면 코드보다 이슈에서 먼저 합의한다.
 *
 * <p><b>날짜·시각을 전부 문자열로 내보내는 이유</b>: iOS 디코더가 기본 전략이라 ISO
 * 문자열을 {@code Date}로 못 읽는다. 시각은 매장 시간대로 잘라 {@code "09:12"} 형태로 준다.
 */
public class MyKindergartenResponse {

    /**
     * 마이 탭의 유치원 섹션 한 줄 (13.2).
     *
     * <p>다견 가구는 <b>강아지 수만큼</b> 행이 나온다 — 알림장이 아이별로 오기 때문이다.
     * 퇴원({@code withdrawn})한 곳은 서버가 뺀다.
     */
    @Schema(description = "내가 다니는 유치원 한 곳")
    public record myItem(
            @Schema(description = "원생 등록 ID. 이후 모든 유치원 API의 진입 키") UUID enrollmentId,
            @Schema(description = "매장 ID") UUID merchantId,
            @Schema(description = "유치원 상호", example = "댕댕유치원 성수점") String merchantName,
            @Schema(description = "강아지 ID. 강아지가 삭제됐으면 null") UUID dogId,
            @Schema(description = "강아지 이름", example = "초코") String dogName,
            @Schema(description = "강아지 프로필 이미지 URL") String dogImageUrl,
            @Schema(description = "등록 상태", allowableValues = {"active", "paused"}, example = "active") String status,
            @Schema(description = "안 읽은 알림장 수. 마이 탭 배지가 이 값 하나로 그려진다", example = "2") int unreadNoteCount
    ) {}

    /**
     * 마이 유치원 홈 (13.3, 화면 C-09).
     */
    @Schema(description = "마이 유치원 홈")
    public record home(
            @Schema(description = "원생 등록 ID") UUID enrollmentId,
            @Schema(description = "유치원 상호", example = "댕댕유치원 성수점") String merchantName,
            @Schema(description = "강아지 이름", example = "초코") String dogName,
            @Schema(description = "등록 상태", allowableValues = {"active", "paused"}, example = "active") String status,

            @Schema(description = """
                    오늘의 등원 상태. **null 이면 "오늘은 등원하지 않는 날"**이다 —
                    `scheduled`("등원 전")와 다르다. 둘을 합치면 안 가는 날이 결석처럼 읽힌다.""")
            todayAttendance todayAttendance,

            @Schema(description = "보유 이용권. 만료·소진된 것은 제외한다") List<pass> passes,
            @Schema(description = "최근 알림장 3건 (FR-C09-03)") List<notePreview> recentNotes
    ) {}

    /**
     * <b>하원은 상태가 아니다.</b> {@code checkedOutAt}이 채워진 것으로 판단한다
     * (점주 쪽 {@code AttendanceStatus}와 동일한 규칙 — 상태를 하나 더 두면
     * "하원했는데 등원 상태" 같은 불가능한 조합이 표현 가능해진다).
     */
    @Schema(description = "오늘의 등원 상태")
    public record todayAttendance(
            @Schema(description = "등원 상태", allowableValues = {"scheduled", "attended", "absent", "cancelled"}, example = "attended")
            String status,
            @Schema(description = "등원 시각(매장 시간대, HH:mm). 아직 등원 전이면 null", example = "09:12") String checkedInAt,
            @Schema(description = "하원 시각(매장 시간대, HH:mm). 아직 원에 있으면 null", example = "16:40") String checkedOutAt
    ) {}

    /**
     * 이용권 한 장.
     *
     * <p><b>기간권이면 {@code remainingCount}·{@code totalCount}가 null</b>이다.
     * iOS는 이걸 보고 "기간권"으로 그린다 — 0으로 채우면 소진된 횟수권과 구분되지 않는다.
     */
    @Schema(description = "이용권")
    public record pass(
            @Schema(description = "이용권 ID") UUID id,
            @Schema(description = "상품명(발급 시점 스냅샷)", example = "10회권") String productName,
            @Schema(description = "잔여 회차. 기간권이면 null", example = "2") Integer remainingCount,
            @Schema(description = "총 회차. 기간권이면 null", example = "10") Integer totalCount,
            @Schema(description = "만료일(yyyy-MM-dd). 무기한이면 null", example = "2026-09-15") String expiresOn,
            @Schema(description = "만료까지 남은 일수. 무기한이면 null, 이미 지났으면 음수", example = "6") Integer daysUntilExpiry
    ) {}

    /**
     * 알림장 미리보기. KG-10(알림장 목록)의 항목과 <b>같은 모양</b>이라 그 티켓에서 재사용한다.
     */
    @Schema(description = "알림장 미리보기")
    public record notePreview(
            @Schema(description = "알림장 ID") UUID id,
            @Schema(description = "알림장 날짜(yyyy-MM-dd)", example = "2026-09-09") String noteDate,
            @Schema(description = "본문 미리보기. 서버가 잘라서 준다", example = "친구들이랑 공놀이 신나게 했어요") String preview,
            @Schema(description = "대표 사진 썸네일 URL. 사진이 없으면 null") String thumbnailUrl,
            @Schema(description = "첨부 사진 수", example = "3") int photoCount,
            @Schema(description = "읽은 시각(ISO). 안 읽었으면 null") String readAt
    ) {}
}
