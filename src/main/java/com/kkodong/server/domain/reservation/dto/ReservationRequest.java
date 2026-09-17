package com.kkodong.server.domain.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class ReservationRequest {

    /**
     * 점주가 대신 등록하는 예약(PN-19). 전화로 받은 예약을 넣는 경로다.
     *
     * <p>이렇게 만든 예약은 {@code source=PARTNER}로 기록되고 곧바로 승인 상태가 된다 —
     * 점주가 직접 넣었다는 것 자체가 승인이므로 자기 예약을 다시 승인하게 만들 이유가 없다.
     *
     * <p>견주 앱에서 신청하는 경로(source=OWNER)는 아직 만들지 않았다.
     */
    public record create(
            @NotNull(message = "원생 ID는 필수입니다.")
            @Schema(description = "예약할 원생 ID", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID enrollmentId,

            @NotNull(message = "이용 날짜는 필수입니다.")
            @Schema(description = "이용 날짜(매장 로컬 기준)", example = "2026-09-10",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate serviceDate,

            @Schema(description = "시작 시각. 생략하면 종일 예약으로 그날 00:00", example = "2026-09-10T09:00:00+09:00")
            OffsetDateTime startsAt,

            @Schema(description = "종료 시각. 생략하면 종일 예약으로 그날 24:00", example = "2026-09-10T18:00:00+09:00")
            OffsetDateTime endsAt,

            @Size(max = 500, message = "요청사항은 500자를 넘을 수 없습니다.")
            @Schema(description = "요청사항") String note
    ) {
        /** 시각은 한 쌍으로만 의미가 있다. 한쪽만 오면 종일인지 시간제인지 알 수 없다. */
        @AssertTrue(message = "시작 시각과 종료 시각은 함께 보내야 합니다.")
        public boolean isPeriodPaired() {
            return (startsAt == null) == (endsAt == null);
        }

        /** 시각을 생략했으면 종일 예약이다(유치원 기본). */
        public boolean isAllDay() {
            return startsAt == null;
        }
    }

    /** 예약 거절(PN-19). 사유는 견주에게 그대로 전달된다. */
    public record reject(
            @Size(max = 500, message = "사유는 500자를 넘을 수 없습니다.")
            @Schema(description = "거절 사유", example = "해당 날짜는 정원이 가득 찼습니다.")
            String reason
    ) {}

    /** 예약 취소(PN-19). 승인 후에도 취소할 수 있다. */
    public record cancel(
            @Size(max = 500, message = "사유는 500자를 넘을 수 없습니다.")
            @Schema(description = "취소 사유") String reason
    ) {}
}
