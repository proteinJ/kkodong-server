package com.kkodong.server.domain.merchant.controller;

import com.kkodong.server.domain.merchant.domain.StaffStatus;
import com.kkodong.server.domain.merchant.dto.MerchantRequest;
import com.kkodong.server.domain.merchant.dto.MerchantResponse;
import com.kkodong.server.domain.merchant.service.MerchantStaffService;
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
 * 꼬동 파트너(점주 앱) 스태프 API — 선생님 소속 신청과 원장의 스태프 관리.
 *
 * <p>흐름 F-18: 선생님이 매장을 검색해 소속 신청 → 원장이 승인하며 권한 부여 →
 * 권한 범위 내 화면만 노출.
 */
@Tag(name = "Partner-Staff", description = "[점주] 선생님 소속 신청·스태프 승인·권한·퇴사 관리 API (PN-03, PN-18)")
@RestController
@RequestMapping("/api/v1/partner")
@RequiredArgsConstructor
public class MerchantStaffController {

    private final MerchantStaffService merchantStaffService;

    @Operation(summary = "선생님 소속 신청 (PN-03)",
            description = """
                    매장을 검색해 고른 뒤 소속을 신청합니다. 승인 대기(PENDING) 상태로 생성되며,
                    원장이 승인해야 매장 데이터에 접근할 수 있습니다.
                    권한은 신청 시점에 정하지 않습니다 — 원장이 승인하며 부여합니다.
                    이미 신청했거나 소속된 매장이면 409(PS004).""")
    @PostMapping("/staff/applications")
    public ResponseEntity<ApiResponse<MerchantResponse.staffItem>> apply(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Valid @RequestBody MerchantRequest.joinStaff request
    ) {
        MerchantResponse.staffItem response = merchantStaffService.apply(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("소속 신청 완료", response));
    }

    @Operation(summary = "스태프 목록 조회 (PN-18)",
            description = """
                    매장 소속 스태프를 상태별로 조회합니다.
                    status를 생략하면 ACTIVE만 내려옵니다. PENDING으로 조회하면 승인 대기 배지에 쓸 수 있습니다.""")
    @GetMapping("/merchants/{merchantId}/staff")
    public ResponseEntity<ApiResponse<List<MerchantResponse.staffItem>>> getStaff(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "소속 상태 필터. 생략 시 ACTIVE") @RequestParam(required = false) StaffStatus status
    ) {
        var response = merchantStaffService.getStaff(merchantId, principal.getUserId(), status);
        return ResponseEntity.ok(ApiResponse.success("스태프 목록 조회 완료", response));
    }

    @Operation(summary = "스태프 승인 및 권한 부여 (PN-18)",
            description = """
                    원장 전용입니다. 승인과 동시에 권한을 부여합니다.
                    권한 값: attendance(출석) · daily_note(알림장) · enrollment(원생정보) · pass(이용권).
                    ⚠️ 이용권 변경(pass)은 기본적으로 원장 전용입니다 — 선생님에게 주려면 명시적으로 포함하세요.
                    원장은 권한 목록과 무관하게 항상 전권을 갖습니다.""")
    @PostMapping("/merchants/{merchantId}/staff/{staffId}/approve")
    public ResponseEntity<ApiResponse<MerchantResponse.staffItem>> approve(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "스태프 ID") @PathVariable UUID staffId,
            @Valid @RequestBody MerchantRequest.approveStaff request
    ) {
        var response = merchantStaffService.approve(merchantId, principal.getUserId(), staffId, request);
        return ResponseEntity.ok(ApiResponse.success("스태프 승인 완료", response));
    }

    @Operation(summary = "스태프 권한 변경 (PN-18)",
            description = """
                    원장 전용입니다. 전달한 목록으로 권한을 통째로 교체합니다(부분 추가/삭제가 아닙니다).
                    빈 배열을 보내면 모든 권한이 해제됩니다.""")
    @PatchMapping("/merchants/{merchantId}/staff/{staffId}/permissions")
    public ResponseEntity<ApiResponse<MerchantResponse.staffItem>> updatePermissions(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "스태프 ID") @PathVariable UUID staffId,
            @Valid @RequestBody MerchantRequest.approveStaff request
    ) {
        var response = merchantStaffService.updatePermissions(merchantId, principal.getUserId(), staffId, request);
        return ResponseEntity.ok(ApiResponse.success("권한 변경 완료", response));
    }

    @Operation(summary = "스태프 퇴사 처리 (PN-18)",
            description = """
                    원장 전용입니다. 접근 권한을 즉시 회수하지만 계정과 작성 이력(알림장·출석 처리)은 보존합니다.
                    ⚠️ 매장의 마지막 원장은 퇴사 처리할 수 없습니다(400, PS005) — 매장을 관리할 사람이 사라집니다.""")
    @DeleteMapping("/merchants/{merchantId}/staff/{staffId}")
    public ResponseEntity<ApiResponse<Void>> resign(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "스태프 ID") @PathVariable UUID staffId
    ) {
        merchantStaffService.resign(merchantId, principal.getUserId(), staffId);
        return ResponseEntity.ok(ApiResponse.success("퇴사 처리 완료"));
    }
}
