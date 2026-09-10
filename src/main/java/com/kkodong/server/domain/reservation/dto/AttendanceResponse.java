package com.kkodong.server.domain.reservation.dto;

import com.kkodong.server.domain.reservation.domain.Attendance;
import com.kkodong.server.domain.reservation.domain.AttendanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class AttendanceResponse {

    /** 등원 기록 한 건(PN-08 카드, PN-09 목록). */
    public record item(
            @Schema(description = "등원 기록 ID") UUID id,
            @Schema(description = "원생 ID") UUID enrollmentId,
            @Schema(description = "강아지 이름") String dogName,
            @Schema(description = "예약 ID. 워크인이면 null") UUID reservationId,
            @Schema(description = "날짜") LocalDate attendanceDate,
            @Schema(description = "상태") AttendanceStatus status,
            @Schema(description = "등원 시각") OffsetDateTime checkedInAt,
            @Schema(description = "하원 시각") OffsetDateTime checkedOutAt,
            @Schema(description = "하원 완료 여부") Boolean checkedOut,
            @Schema(description = "차감된 이용권 ID. 기간권·미등원이면 null") UUID passId,
            @Schema(description = "되돌린 시각. null이면 되돌린 적 없음") OffsetDateTime revertedAt,
            @Schema(description = "지금 등원 처리할 수 있는지. 사용 가능한 이용권이 없으면 false")
            Boolean checkInAvailable,
            @Schema(description = "등원 처리를 막는 이유. checkInAvailable=false일 때만 채워진다")
            String blockedReason
    ) {
        /**
         * @param dogName 원생 스냅샷에서 가져온다 — 견주가 탈퇴해도 이름이 남아야 한다
         * @param checkInAvailable FR-PN09-02 — 이용권이 만료·소진된 원생은 선택 대상에서 빠진다.
         *        목록에서 아예 감추지 않고 이유와 함께 보여주는 이유는, 점주가 "왜 이 아이가
         *        안 보이지"를 겪는 대신 "이용권을 발급해야겠다"로 바로 가게 하려는 것이다.
         */
        public static item of(Attendance a, String dogName,
                              boolean checkInAvailable, String blockedReason) {
            return new item(
                    a.getId(), a.getEnrollmentId(), dogName, a.getReservationId(),
                    a.getAttendanceDate(), a.getStatus(),
                    a.getCheckedInAt(), a.getCheckedOutAt(), a.isCheckedOut(),
                    a.getPassId(), a.getRevertedAt(),
                    checkInAvailable, blockedReason
            );
        }
    }

    /**
     * PN-08 출석 대시보드. 점주 앱 로그인 직후 첫 화면이다.
     *
     * <p>FR-PN08-01의 "미등원을 시각적으로 강조"는 {@code scheduledCount}가 담당한다 —
     * 이 수가 0이 되는 것이 오전 운영의 목표다.
     */
    public record dashboard(
            @Schema(description = "날짜") LocalDate date,
            @Schema(description = "등원 예정(아직 안 옴)") long scheduledCount,
            @Schema(description = "등원함(하원 전)") long attendedCount,
            @Schema(description = "하원 완료") long checkedOutCount,
            @Schema(description = "결석") long absentCount,
            @Schema(description = "당일 취소") long cancelledCount,
            @Schema(description = "등원 기록 전체") List<item> items
    ) {}

    /**
     * 다중 등·하원 처리 결과(FR-PN09-01).
     *
     * <p><b>왜 부분 성공을 허용하는가</b>: 10마리를 한꺼번에 처리하는데 1마리의 이용권이
     * 만료됐다고 나머지 9마리까지 되돌리면, 점주는 원인을 모른 채 다시 눌러야 한다.
     * 성공한 것은 확정하고 실패한 것만 이유와 함께 돌려준다.
     *
     * <p>⚠️ 단 <b>한 건 안에서는</b> 등원 확정과 이용권 차감이 원자적이다(FR-PN09-03).
     * 부분 성공은 건과 건 사이에만 허용된다.
     */
    public record bulkResult(
            @Schema(description = "처리 성공한 등원 기록 ID") List<UUID> succeeded,
            @Schema(description = "실패한 건과 사유") List<failure> failed
    ) {
        public record failure(
                @Schema(description = "등원 기록 ID") UUID attendanceId,
                @Schema(description = "강아지 이름") String dogName,
                @Schema(description = "에러 코드", example = "PA001") String code,
                @Schema(description = "실패 사유") String message
        ) {}
    }
}
