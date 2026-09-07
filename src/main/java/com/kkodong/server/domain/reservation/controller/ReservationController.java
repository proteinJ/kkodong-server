package com.kkodong.server.domain.reservation.controller;

import com.kkodong.server.domain.reservation.domain.ReservationStatus;
import com.kkodong.server.domain.reservation.dto.ReservationRequest;
import com.kkodong.server.domain.reservation.dto.ReservationResponse;
import com.kkodong.server.domain.reservation.service.ReservationService;
import com.kkodong.server.global.common.ApiResponse;
import com.kkodong.server.global.security.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 꼬동 파트너(점주 앱) 예약 API — PN-19 예약 관리.
 *
 * <p><b>예약 → 등원 전환</b>: 승인하면 그 자리에서 등원 예정(attendances)이 만들어져
 * 출석 대시보드(PN-08)에 나타난다. 이용권 차감은 여기가 아니라 실제 등원 확정(PN-09)
 * 시점이다 — 회차는 서비스를 받은 사실에 대응해야 하기 때문이다.
 *
 * <p>출석 권한(attendance)이 있으면 다룰 수 있다. 예약 확인·승인은 현장에서 선생님이
 * 하는 일이라 원장 전용으로 묶으면 매장이 돌지 않는다.
 */
@Tag(name = "Partner-Reservation",
        description = "[점주] 예약 확인·승인·거절·취소·캘린더 API (PN-19). 승인 시 등원 예정으로 전환된다")
@RestController
@RequestMapping("/api/v1/partner/merchants/{merchantId}/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "예약 대신 등록 (PN-19)",
            description = """
                    점주가 전화 등으로 받은 예약을 대신 등록합니다. source=PARTNER로 기록되며
                    등록 즉시 확정(CONFIRMED)되고 등원 예정이 함께 만들어집니다 —
                    점주가 직접 넣은 것 자체가 승인이라 다시 승인하게 만들지 않습니다.

                    시작/종료 시각을 생략하면 종일 예약(유치원 기본)입니다. 보내려면 둘 다 보내야 합니다.

                    실패: 재원 중이 아닌 원생 400(EN002) · 같은 날 중복 409(RV003) · 정원 초과 409(RV004)""")
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse.confirmResult>> create(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Valid @RequestBody ReservationRequest.create request
    ) {
        var response = reservationService.createByPartner(merchantId, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("예약 등록 완료", response));
    }

    @Operation(summary = "예약 캘린더 조회 (PN-19)",
            description = "기간 내 예약을 날짜·시작시각 순으로 반환합니다. 캘린더 화면에 그대로 그리면 됩니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReservationResponse.detailInfo>>> getCalendar(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "조회 시작일", example = "2026-09-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "조회 종료일", example = "2026-09-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        var response = reservationService.getCalendar(merchantId, principal.getUserId(), from, to);
        return ResponseEntity.ok(ApiResponse.success("예약 캘린더 조회 완료", response));
    }

    @Operation(summary = "상태별 예약 목록 (PN-19)",
            description = "REQUESTED로 조회하면 승인 대기함이 됩니다. 홈 배지 카운트도 여기서 나옵니다.")
    @GetMapping("/by-status")
    public ResponseEntity<ApiResponse<List<ReservationResponse.detailInfo>>> getByStatus(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "예약 상태") @RequestParam ReservationStatus status
    ) {
        var response = reservationService.getByStatus(merchantId, principal.getUserId(), status);
        return ResponseEntity.ok(ApiResponse.success("예약 목록 조회 완료", response));
    }

    @Operation(summary = "날짜별 예약 집계 (PN-19)",
            description = """
                    해당 날짜의 승인 대기·확정 건수와 정원 대비 남은 자리를 반환합니다.
                    일일 정원이 설정되지 않았으면(무제한) dailyCapacity와 remainingSlots가 null입니다.""")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ReservationResponse.dailySummary>> getDailySummary(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "집계할 날짜", example = "2026-09-10")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        var response = reservationService.getDailySummary(merchantId, principal.getUserId(), date);
        return ResponseEntity.ok(ApiResponse.success("예약 집계 조회 완료", response));
    }

    @Operation(summary = "예약 상세 조회 (PN-19)",
            description = "매장 소속 스태프만 조회할 수 있습니다. 다른 매장의 예약 ID면 404(RV001).")
    @GetMapping("/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponse.detailInfo>> get(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "예약 ID") @PathVariable UUID reservationId
    ) {
        var response = reservationService.get(merchantId, principal.getUserId(), reservationId);
        return ResponseEntity.ok(ApiResponse.success("예약 조회 완료", response));
    }

    @Operation(summary = "예약 승인 (PN-19) ★ 등원 전환 지점",
            description = """
                    신청(REQUESTED) 상태의 예약을 확정하고, 그 자리에서 등원 예정(attendances)을 만듭니다.
                    이 등원 예정이 출석 대시보드(PN-08)에 나타납니다.

                    ⚠️ 이용권은 여기서 차감되지 않습니다 — 실제 등원 확정(PN-09) 시점에 차감됩니다.
                    따라서 승인 시점에 잔여가 충분해도 등원일에는 0일 수 있습니다(정상 상황).

                    승인 직전 (매장, 날짜) 단위 락을 잡고 정원을 검사하므로 동시 승인으로 정원을 넘지 않습니다.

                    실패: 신청 상태가 아님 400(RV002) · 정원 초과 409(RV004)""")
    @PostMapping("/{reservationId}/confirm")
    public ResponseEntity<ApiResponse<ReservationResponse.confirmResult>> confirm(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "예약 ID") @PathVariable UUID reservationId
    ) {
        var response = reservationService.confirm(merchantId, principal.getUserId(), reservationId);
        return ResponseEntity.ok(ApiResponse.success("예약 승인 완료", response));
    }

    @Operation(summary = "예약 거절 (PN-19)",
            description = "신청 상태의 예약만 거절할 수 있습니다(400, RV002). 사유는 견주에게 그대로 전달됩니다.")
    @PostMapping("/{reservationId}/reject")
    public ResponseEntity<ApiResponse<ReservationResponse.detailInfo>> reject(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "예약 ID") @PathVariable UUID reservationId,
            @Valid @RequestBody ReservationRequest.reject request
    ) {
        var response = reservationService.reject(merchantId, principal.getUserId(), reservationId, request);
        return ResponseEntity.ok(ApiResponse.success("예약 거절 완료", response));
    }

    @Operation(summary = "예약 취소 (PN-19)",
            description = """
                    신청 중이든 승인 후든 취소할 수 있습니다 — 당일 사정으로 못 오는 일은 정상입니다.
                    승인된 예약을 취소하면 함께 만들어진 등원 예정도 접힙니다.

                    ⚠️ 이미 등원한 뒤라면 등원 기록은 건드리지 않습니다. 온 사실과 차감된 회차를
                    되돌리는 것은 취소가 아니라 되돌리기(PN-09)이며 이용권 복원이 따라붙어야 합니다.

                    실패: 이미 끝난 예약(완료·노쇼·거절·기취소) 400(RV002)""")
    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<ApiResponse<ReservationResponse.detailInfo>> cancel(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "예약 ID") @PathVariable UUID reservationId,
            @Valid @RequestBody ReservationRequest.cancel request
    ) {
        var response = reservationService.cancel(merchantId, principal.getUserId(), reservationId, request);
        return ResponseEntity.ok(ApiResponse.success("예약 취소 완료", response));
    }
}
