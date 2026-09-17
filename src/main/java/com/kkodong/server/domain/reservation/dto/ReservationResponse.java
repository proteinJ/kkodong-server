package com.kkodong.server.domain.reservation.dto;

import com.kkodong.server.domain.reservation.domain.Reservation;
import com.kkodong.server.domain.reservation.domain.ReservationSource;
import com.kkodong.server.domain.reservation.domain.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ReservationResponse {

    /** 예약 상세·목록 공통 항목(PN-19). */
    public record detailInfo(
            @Schema(description = "예약 ID") UUID id,
            @Schema(description = "매장 ID") UUID merchantId,
            @Schema(description = "원생 ID. 미용실·병원은 null") UUID enrollmentId,
            @Schema(description = "강아지 이름. 견주 탈퇴 후에도 남는 스냅샷") String dogName,
            @Schema(description = "이용 날짜") LocalDate serviceDate,
            @Schema(description = "시작 시각") OffsetDateTime startsAt,
            @Schema(description = "종료 시각") OffsetDateTime endsAt,
            @Schema(description = "종일 예약 여부") Boolean isAllDay,
            @Schema(description = "예약 상태") ReservationStatus status,
            @Schema(description = "예약 경로") ReservationSource source,
            @Schema(description = "요청사항") String note,
            @Schema(description = "거절 사유") String rejectReason,
            @Schema(description = "취소 사유") String cancelReason,
            @Schema(description = "응답(승인·거절) 시각") OffsetDateTime respondedAt,
            @Schema(description = "생성 시각") OffsetDateTime createdAt
    ) {
        public static detailInfo from(Reservation r) {
            return new detailInfo(
                    r.getId(), r.getMerchantId(), r.getEnrollmentId(), r.getDogNameSnapshot(),
                    r.getServiceDate(), r.getStartsAt(), r.getEndsAt(), r.getIsAllDay(),
                    r.getStatus(), r.getSource(), r.getNote(),
                    r.getRejectReason(), r.getCancelReason(),
                    r.getRespondedAt(), r.getCreatedAt()
            );
        }
    }

    /**
     * 승인 응답. 예약과 함께 "이 원생의 이용권이 등원일까지 버티는가"를 알려준다.
     *
     * <p><b>왜 경고를 함께 내려주는가</b>: 이용권 차감은 예약 승인이 아니라 등원 확정
     * 시점에 일어난다(V8 설계). 그래서 승인 시점에 잔여가 충분해도 등원일에는 0일 수
     * 있고, 반대로 지금 0이어도 그 전에 충전하면 된다. 승인을 막지는 않되 점주가 모르고
     * 지나치지 않도록 신호만 준다.
     *
     * <p>⚠️ 이용권 도메인(PN-12)이 아직 없어 지금은 항상 null이다. 필드를 미리 두는 이유는
     * 나중에 채울 때 응답 계약이 바뀌지 않게 하려는 것이다.
     */
    public record confirmResult(
            @Schema(description = "승인된 예약") detailInfo reservation,
            @Schema(description = "생성된 등원 예정 ID") UUID attendanceId,
            @Schema(description = "이용권 잔여 부족 경고. 이용권 도메인 구현 전까지 null")
            String passWarning
    ) {}

    /** PN-19 캘린더의 날짜별 집계. 정원 대비 얼마나 찼는지 한눈에 보여준다. */
    public record dailySummary(
            @Schema(description = "날짜") LocalDate serviceDate,
            @Schema(description = "승인 대기 건수") long requestedCount,
            @Schema(description = "확정 건수(정원을 차지하는 수)") long confirmedCount,
            @Schema(description = "일일 정원. null이면 무제한") Integer dailyCapacity,
            @Schema(description = "남은 자리. 정원이 무제한이면 null") Integer remainingSlots
    ) {}
}
