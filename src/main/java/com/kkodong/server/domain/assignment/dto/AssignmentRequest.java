package com.kkodong.server.domain.assignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class AssignmentRequest {

    /**
     * 담당 배정(PN-13). 여러 강아지를 한 선생님에게 한 번에 맡긴다.
     *
     * <p>이미 다른 선생님에게 배정된 강아지는 이쪽으로 <b>옮겨진다</b> — 아침에 배정을
     * 조정하는 것이 정상 작업이고, "이미 배정됨"으로 막으면 먼저 해제하는 두 번의 조작이
     * 필요해진다.
     */
    public record assign(
            @NotNull(message = "담당 선생님은 필수입니다.")
            @Schema(description = "담당할 스태프 ID (merchant_staff.id — users.id가 아니다)",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            UUID staffId,

            @NotEmpty(message = "배정할 원생을 하나 이상 선택해야 합니다.")
            @Size(max = 100, message = "한 번에 100명까지 배정할 수 있습니다.")
            @Schema(description = "배정할 원생 ID 목록", requiredMode = Schema.RequiredMode.REQUIRED)
            List<UUID> enrollmentIds,

            @NotNull(message = "날짜는 필수입니다.")
            @Schema(description = "배정 날짜", example = "2026-09-08",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate assignedDate
    ) {}

    /** 배정 해제(PN-13). */
    public record unassign(
            @NotEmpty(message = "해제할 원생을 하나 이상 선택해야 합니다.")
            @Schema(description = "배정을 해제할 원생 ID 목록", requiredMode = Schema.RequiredMode.REQUIRED)
            List<UUID> enrollmentIds,

            @NotNull(message = "날짜는 필수입니다.")
            @Schema(description = "배정 날짜", requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate assignedDate
    ) {}
}
