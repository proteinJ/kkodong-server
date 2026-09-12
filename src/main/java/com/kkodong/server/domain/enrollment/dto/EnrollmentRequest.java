package com.kkodong.server.domain.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public class EnrollmentRequest {

    /**
     * 특이사항 메모 수정(PN-11).
     *
     * <p>⚠️ 보호자에게 노출되지 않는 내부 메모다. null을 보내면 메모를 지운다 —
     * 다른 PATCH들과 달리 "안 바꿈"이 아니다. 필드가 하나뿐이라 그 해석이 더 자연스럽다.
     */
    public record updateMemo(
            @Size(max = 2000, message = "메모는 2000자를 넘을 수 없습니다.")
            @Schema(description = "특이사항 메모. 보호자 비공개",
                    example = "낯선 사람을 무서워함. 오전에는 혼자 두는 편이 좋음")
            String staffMemo
    ) {}
}
