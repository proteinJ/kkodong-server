package com.kkodong.server.domain.enrollment.controller;

import com.kkodong.server.domain.enrollment.dto.PassRequest;
import com.kkodong.server.domain.enrollment.dto.PassResponse;
import com.kkodong.server.domain.enrollment.service.PassService;
import com.kkodong.server.global.common.ApiResponse;
import com.kkodong.server.global.security.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 꼬동 파트너(점주 앱) 이용권 API — PN-12 이용권 관리.
 *
 * <p><b>매출 데이터를 다룬다.</b> 모든 변동은 원장(pass_ledger)에 한 줄을 남기며,
 * 원장을 처음부터 더하면 현재 잔여가 나와야 한다(FR-PN12-01).
 *
 * <p>⚠️ 권한은 {@code pass}이며 FR-PN18-01에 따라 <b>기본 원장 전용</b>이다.
 * 선생님에게 주려면 원장이 스태프 권한에서 명시적으로 켜야 한다.
 */
@Tag(name = "Partner-Pass",
        description = "[점주] 이용권 발급·연장·환불·조정·변동이력 API (PN-12). 모든 변동이 감사 로그로 남는다")
@RestController
@RequestMapping("/api/v1/partner/merchants/{merchantId}/passes")
@RequiredArgsConstructor
public class PassController {

    private final PassService passService;

    @Operation(summary = "이용권 발급 (PN-12)",
            description = """
                    원생에게 이용권을 발급합니다. 신청 승인(PN-07) 직후나 재결제 시 호출합니다.

                    회차·유효기간·가격은 요청으로 받지 않고 상품(PN-04) 정의에서 가져와 발급 시점
                    스냅샷으로 굳힙니다 — 상품 가격이 나중에 바뀌어도 이미 팔린 이용권의 조건은
                    그대로여야 하고, 클라이언트가 조건을 보내면 화면에 보인 가격과 실제 발급분이
                    달라져 매출 분쟁이 됩니다.

                    판매 중단된 상품으로도 발급됩니다 — 지면으로 이미 판 이용권을 뒤늦게 입력하는
                    일이 실제로 생기기 때문입니다.

                    실패: 원생 없음 404(EN001) · 상품 없음 404(P004) · 권한 없음 403(PS002)""")
    @PostMapping
    public ResponseEntity<ApiResponse<PassResponse.detailInfo>> issue(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Valid @RequestBody PassRequest.issue request
    ) {
        var response = passService.issue(merchantId, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("이용권 발급 완료", response));
    }

    @Operation(summary = "원생의 이용권 목록 (PN-11, PN-12)",
            description = """
                    사용 가능한 것을 위로, 그다음 만료 임박순으로 정렬해 반환합니다.

                    usableToday는 상태·잔여·만료를 모두 본 결과입니다 — 이 값만 보면 되고
                    status/remainingCount/expiresOn을 각각 따져 판정하지 마세요(하나를 빠뜨리기 쉽습니다).
                    daysUntilExpiry가 음수면 이미 만료된 것입니다.""")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PassResponse.detailInfo>>> getByEnrollment(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "원생 ID") @RequestParam UUID enrollmentId
    ) {
        var response = passService.getByEnrollment(merchantId, principal.getUserId(), enrollmentId);
        return ResponseEntity.ok(ApiResponse.success("이용권 목록 조회 완료", response));
    }

    @Operation(summary = "이용권 변동 이력 (PN-12, KG-13)",
            description = """
                    발급·차감·복원·연장·환불·조정 전체를 시간 역순으로 반환합니다(FR-PN12-01 감사 로그).

                    이 목록을 처음부터 더하면 현재 잔여가 나와야 합니다 — 어긋나면 balanceAfter로
                    어느 지점에서 깨졌는지 찾을 수 있습니다.

                    등원 차감에는 sourceAttendanceId가 붙어 있어 어느 날 등원이 어떤 회차를 썼는지
                    이어볼 수 있습니다(KG-13 등원일별 차감 내역).""")
    @GetMapping("/{passId}/ledger")
    public ResponseEntity<ApiResponse<List<PassResponse.ledgerItem>>> getLedger(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "이용권 ID") @PathVariable UUID passId
    ) {
        var response = passService.getLedger(merchantId, principal.getUserId(), passId);
        return ResponseEntity.ok(ApiResponse.success("변동 이력 조회 완료", response));
    }

    @Operation(summary = "이용권 기간 연장 (PN-12)",
            description = """
                    유효기간만 늘립니다. 회차는 건드리지 않습니다.

                    기준점은 오늘이 아니라 기존 만료일입니다 — 만료 전에 미리 연장해도 남은 기간을
                    깎아먹지 않습니다. 이미 만료된 뒤라면 오늘부터 셉니다.
                    만료로 닫혔던 이용권은 다시 열리지만, 회차까지 소진됐다면 EXHAUSTED로 남습니다.

                    실패: 이미 환불됨 400(PA004)""")
    @PostMapping("/{passId}/extend")
    public ResponseEntity<ApiResponse<PassResponse.detailInfo>> extend(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "이용권 ID") @PathVariable UUID passId,
            @Valid @RequestBody PassRequest.extend request
    ) {
        var response = passService.extend(merchantId, principal.getUserId(), passId, request);
        return ResponseEntity.ok(ApiResponse.success("기간 연장 완료", response));
    }

    @Operation(summary = "이용권 환불 (PN-12)",
            description = """
                    ⚠️ 되돌릴 수 없는 종료 처리입니다. 사유가 필수입니다.

                    남아 있던 회차를 0으로 떨어뜨립니다 — 남겨두면 환불된 이용권으로 등원이 되어
                    매출과 장부가 어긋납니다. 원장에는 걷어낸 회차만큼 음수 변동이 기록되므로,
                    이력을 처음부터 더하면 0이 나옵니다.

                    실패: 이미 환불됨 400(PA004)""")
    @PostMapping("/{passId}/refund")
    public ResponseEntity<ApiResponse<PassResponse.detailInfo>> refund(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "이용권 ID") @PathVariable UUID passId,
            @Valid @RequestBody PassRequest.refund request
    ) {
        var response = passService.refund(merchantId, principal.getUserId(), passId, request);
        return ResponseEntity.ok(ApiResponse.success("환불 처리 완료", response));
    }

    @Operation(summary = "이용권 회차 수동 조정 (PN-12)",
            description = """
                    착오 입력 정정이나 서비스 회차 제공 등 예외 상황용입니다.
                    ⚠️ 일상적인 차감에 쓰지 마세요 — 등원 차감은 출석 체크(PN-09)가 자동으로 합니다.

                    사유가 필수입니다. 근거 없는 회차 변동은 분쟁 때 방어할 수 없습니다.
                    처리자와 사유가 원장에 남습니다.

                    실패: 조정 후 잔여가 음수 400(PA005) · 기간권 400(PA005) · 이미 환불됨 400(PA004)""")
    @PostMapping("/{passId}/adjust")
    public ResponseEntity<ApiResponse<PassResponse.detailInfo>> adjust(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "이용권 ID") @PathVariable UUID passId,
            @Valid @RequestBody PassRequest.adjust request
    ) {
        var response = passService.adjust(merchantId, principal.getUserId(), passId, request);
        return ResponseEntity.ok(ApiResponse.success("회차 조정 완료", response));
    }
}
