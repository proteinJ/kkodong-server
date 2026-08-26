package com.kkodong.server.domain.safety.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class ReportRequest {

    /**
     * enum 값을 String으로 받는 이유: 요청 JSON은 소문자 스네이크(`"inappropriate_behavior"`)인데
     * Java enum 상수는 대문자라 Jackson 기본 역직렬화가 실패한다. 실패하면 400이 아니라
     * HttpMessageNotReadableException으로 빠져 어느 필드가 틀렸는지 알려주지 못한다.
     * Dog의 gender/size/energyLevel과 같은 방식으로 서비스에서 변환·검증한다.
     */
    public record create(
            @NotBlank(message = "신고 대상 유형은 필수입니다.")
            @Schema(description = "신고 대상 유형", example = "user",
                    allowableValues = {"user", "community_post", "community_comment"},
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String targetType,

            @NotNull(message = "신고 대상 ID는 필수입니다.")
            @Schema(description = "신고 대상 ID", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID targetId,

            @NotBlank(message = "신고 사유는 필수입니다.")
            @Schema(description = "신고 사유", example = "harassment",
                    allowableValues = {"inappropriate_behavior", "safety_concern", "harassment",
                                       "fake_profile", "spam", "other"},
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String reason,

            @Size(max = 1000, message = "상세 설명은 1000자를 넘을 수 없습니다.")
            @Schema(description = "상세 설명(선택)", example = "채팅에서 지속적으로 불쾌한 메시지를 보냅니다.")
            String details
    ) {}
}
