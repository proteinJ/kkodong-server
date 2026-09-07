package com.kkodong.server.domain.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.UUID;

public class PassRequest {

    /**
     * 이용권 발급(PN-12). 신청 승인(PN-07) 직후 또는 재결제 때 호출된다.
     *
     * <p>회차·유효기간·가격을 요청으로 받지 않는다 — 상품(PN-04) 정의에서 가져와
     * 발급 시점 스냅샷으로 굳힌다. 클라이언트가 보내게 하면 화면에 보이는 가격과
     * 실제 발급 조건이 달라질 수 있고, 그건 매출 분쟁의 씨앗이다.
     */
    public record issue(
            @NotNull(message = "원생 ID는 필수입니다.")
            @Schema(description = "이용권을 발급할 원생 ID", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID enrollmentId,

            @NotNull(message = "상품 ID는 필수입니다.")
            @Schema(description = "발급할 이용권 상품 ID", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID productId,

            @Size(max = 500, message = "메모는 500자를 넘을 수 없습니다.")
            @Schema(description = "발급 메모. 감사 로그에 남는다", example = "9월 재결제")
            String reason
    ) {}

    /** 기간 연장(PN-12). 회차는 건드리지 않는다. */
    public record extend(
            @Positive(message = "연장일수는 1 이상이어야 합니다.")
            @NotNull(message = "연장일수는 필수입니다.")
            @Schema(description = "연장할 일수", example = "30", requiredMode = Schema.RequiredMode.REQUIRED)
            Integer days,

            @Size(max = 500, message = "사유는 500자를 넘을 수 없습니다.")
            @Schema(description = "연장 사유", example = "휴원 기간 보상") String reason
    ) {}

    /** 환불(PN-12). 되돌릴 수 없다. */
    public record refund(
            @NotBlank(message = "환불 사유는 필수입니다.")
            @Size(max = 500, message = "사유는 500자를 넘을 수 없습니다.")
            @Schema(description = "환불 사유. 매출이 줄어드는 처리라 근거를 반드시 남긴다",
                    example = "이사로 인한 중도 해지", requiredMode = Schema.RequiredMode.REQUIRED)
            String reason
    ) {}

    /**
     * 수동 조정(PN-12). 착오 입력 정정이나 서비스 회차 제공 등 예외 상황용이다.
     *
     * <p>⚠️ 사유가 필수다 — 근거 없는 회차 변동은 분쟁 때 방어할 수 없다.
     */
    public record adjust(
            @NotNull(message = "증감량은 필수입니다.")
            @Schema(description = "증감량. 음수면 차감", example = "2",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            Integer delta,

            @NotBlank(message = "조정 사유는 필수입니다.")
            @Size(max = 500, message = "사유는 500자를 넘을 수 없습니다.")
            @Schema(description = "조정 사유", example = "등원 누락분 2회 보정",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String reason
    ) {}
}
