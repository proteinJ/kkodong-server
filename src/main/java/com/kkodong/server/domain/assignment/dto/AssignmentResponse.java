package com.kkodong.server.domain.assignment.dto;

import com.kkodong.server.domain.merchant.domain.StaffRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class AssignmentResponse {

    /**
     * PN-13 배정 현황(하루치).
     *
     * <p><b>마릿수가 배정만큼 중요한 출력이다</b>(FR-PN13-01). 이 화면의 목적은 배정 자체가
     * 아니라 <b>선생님 한 명에게 몰리지 않게 하는 것</b>이므로, 담당 수가 한눈에 보여야 한다.
     * {@code unassigned}가 비고 부하가 고르면 배정이 끝난 것이다.
     */
    public record board(
            @Schema(description = "날짜") LocalDate date,
            @Schema(description = "오늘 등원한 원생 수") int attendedCount,
            @Schema(description = "선생님별 담당 현황. 담당 수가 적은 순으로 정렬된다")
            List<staffLoad> staff,
            @Schema(description = "아직 담당이 없는 원생 — 이 목록이 비면 배정이 끝난다")
            List<assignedDog> unassigned
    ) {
        /** 선생님 한 명의 담당 현황. */
        public record staffLoad(
                @Schema(description = "스태프 ID (merchant_staff.id)") UUID staffId,
                @Schema(description = "유저 ID") UUID userId,
                @Schema(description = "이름") String displayName,
                @Schema(description = "역할") StaffRole role,
                @Schema(description = "담당 마릿수") int count,
                @Schema(description = "담당 강아지") List<assignedDog> dogs
        ) {}

        public record assignedDog(
                @Schema(description = "원생 ID") UUID enrollmentId,
                @Schema(description = "강아지 이름") String dogName
        ) {}
    }
}
