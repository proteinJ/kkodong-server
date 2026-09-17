package com.kkodong.server.domain.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public class AttendanceRequest {

    /**
     * 다중 선택 원클릭 등·하원(FR-PN09-01). 아침에 여러 마리가 한꺼번에 도착하므로
     * 한 건씩 부르게 하면 현장에서 쓰이지 않는다.
     */
    public record bulk(
            @NotEmpty(message = "대상을 하나 이상 선택해야 합니다.")
            @Size(max = 100, message = "한 번에 100건까지 처리할 수 있습니다.")
            @Schema(description = "처리할 등원 기록 ID 목록", requiredMode = Schema.RequiredMode.REQUIRED)
            List<UUID> attendanceIds
    ) {}

    /** 등원 되돌리기(FR-PN09-04). 차감된 이용권도 함께 복원된다. */
    public record revert(
            @Size(max = 500, message = "사유는 500자를 넘을 수 없습니다.")
            @Schema(description = "되돌리는 사유. 감사 로그에 남는다", example = "잘못 눌러 등원 처리함")
            String reason
    ) {}
}
