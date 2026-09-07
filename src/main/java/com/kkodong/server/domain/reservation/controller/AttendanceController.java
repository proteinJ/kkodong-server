package com.kkodong.server.domain.reservation.controller;

import com.kkodong.server.domain.reservation.dto.AttendanceRequest;
import com.kkodong.server.domain.reservation.dto.AttendanceResponse;
import com.kkodong.server.domain.reservation.service.AttendanceService;
import com.kkodong.server.global.common.ApiResponse;
import com.kkodong.server.global.security.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 꼬동 파트너(점주 앱) 출석 API — PN-08 대시보드, PN-09 출석 체크.
 *
 * <p>예약 승인으로 쌓인 등원 예정을 실제로 소비하는 쪽이다. 등원 확정은 이용권 1회 차감과
 * 하나의 트랜잭션으로 처리된다(FR-PN09-03).
 */
@Tag(name = "Partner-Attendance",
        description = "[점주] 출석 대시보드·등원/하원·되돌리기 API (PN-08, PN-09). 등원 확정 시 이용권이 차감된다")
@RestController
@RequestMapping("/api/v1/partner/merchants/{merchantId}/attendances")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @Operation(summary = "출석 대시보드 (PN-08)",
            description = """
                    해당 날짜의 등원 예정/등원/하원/결석/취소 현황과 전체 목록을 반환합니다.
                    점주 앱 로그인 직후 첫 화면입니다. scheduledCount(아직 안 온 수)가 0이 되는 것이
                    오전 운영의 목표이며, 이 값을 강조해 보여주면 됩니다.

                    각 항목의 checkInAvailable이 false면 사용 가능한 이용권이 없다는 뜻이고
                    blockedReason에 이유가 담깁니다 — 목록에서 감추지 않고 이유를 보여주므로
                    점주가 바로 이용권 발급으로 갈 수 있습니다.""")
    @GetMapping
    public ResponseEntity<ApiResponse<AttendanceResponse.dashboard>> getDashboard(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "조회할 날짜", example = "2026-10-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        var response = attendanceService.getDashboard(merchantId, principal.getUserId(), date);
        return ResponseEntity.ok(ApiResponse.success("출석 현황 조회 완료", response));
    }

    @Operation(summary = "등원 처리 (PN-09) ★ 이용권 차감 지점",
            description = """
                    선택한 원생들을 한 번에 등원 처리합니다(다중 선택 원클릭).

                    ★ 건별로 [등원 확정 + 이용권 1회 차감 + 차감 로그]가 하나의 트랜잭션입니다.
                    셋 중 하나라도 실패하면 그 건은 통째로 되돌아갑니다 — "등원은 됐는데 회차가
                    안 깎인" 상태는 조회로 찾아낼 방법이 없기 때문입니다.

                    ⚠️ 부분 성공을 허용합니다. 10마리 중 1마리의 이용권이 만료됐다고 나머지 9마리를
                    되돌리면 점주는 원인을 모른 채 다시 눌러야 합니다. succeeded/failed로 나눠 반환하니
                    failed만 화면에 남겨 주세요.

                    이용권은 만료일이 빠른 것부터 차감합니다 — 반대로 하면 유효기간이 남은 이용권을
                    놔둔 채 다른 것을 먼저 태워 결국 하나가 통째로 만료됩니다.

                    실패 사유: 사용 가능한 이용권 없음(PA001) · 등원 예정 상태가 아님(AT002)""")
    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<AttendanceResponse.bulkResult>> checkIn(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Valid @RequestBody AttendanceRequest.bulk request
    ) {
        var response = attendanceService.checkIn(merchantId, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("등원 처리 완료", response));
    }

    @Operation(summary = "하원 처리 (PN-09)",
            description = """
                    선택한 원생들을 한 번에 하원 처리합니다. 이용권과 무관합니다.

                    하원은 별도 상태가 아니라 checkedOutAt이 채워진 것으로 표현됩니다 —
                    상태로 만들면 "하원했지만 등원 안 함" 같은 불가능한 조합이 생깁니다.
                    등원하지 않은 원생은 하원할 수 없습니다(AT002).""")
    @PostMapping("/check-out")
    public ResponseEntity<ApiResponse<AttendanceResponse.bulkResult>> checkOut(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Valid @RequestBody AttendanceRequest.bulk request
    ) {
        var response = attendanceService.checkOut(merchantId, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("하원 처리 완료", response));
    }

    @Operation(summary = "등원 되돌리기 (PN-09)",
            description = """
                    잘못 처리한 등원을 취소하고 등원 예정 상태로 되돌립니다.
                    ★ 차감된 이용권 회차도 함께 복원됩니다.

                    ⚠️ 복원은 차감 기록을 지우는 것이 아니라 반대 방향 기록(RESTORE)을 추가하는
                    방식입니다 — 이용권 원장은 append-only이며, 되돌린 이력 자체가 분쟁 대응의
                    근거입니다. 등원 기록도 지우지 않고 revertedAt을 남깁니다.

                    등원 상태(ATTENDED)가 아니면 400(AT002).""")
    @PostMapping("/{attendanceId}/revert")
    public ResponseEntity<ApiResponse<AttendanceResponse.item>> revert(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "등원 기록 ID") @PathVariable UUID attendanceId,
            @Valid @RequestBody AttendanceRequest.revert request
    ) {
        var response = attendanceService.revert(merchantId, principal.getUserId(), attendanceId, request);
        return ResponseEntity.ok(ApiResponse.success("등원 되돌리기 완료", response));
    }

    @Operation(summary = "결석 처리 (PN-08)",
            description = "등원 예정이었으나 오지 않은 원생을 결석 처리합니다. 이용권은 차감되지 않습니다.")
    @PostMapping("/{attendanceId}/absent")
    public ResponseEntity<ApiResponse<AttendanceResponse.item>> markAbsent(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "등원 기록 ID") @PathVariable UUID attendanceId
    ) {
        var response = attendanceService.markAbsent(merchantId, principal.getUserId(), attendanceId);
        return ResponseEntity.ok(ApiResponse.success("결석 처리 완료", response));
    }
}
