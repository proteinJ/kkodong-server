package com.kkodong.server.domain.assignment.controller;

import com.kkodong.server.domain.assignment.dto.AssignmentRequest;
import com.kkodong.server.domain.assignment.dto.AssignmentResponse;
import com.kkodong.server.domain.assignment.service.AssignmentService;
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
 * 꼬동 파트너(점주 앱) 담당 배정 API — PN-13.
 *
 * <p>오늘 등원한 강아지를 선생님별로 나눈다. 목적은 배정 자체가 아니라
 * <b>한 명에게 몰리지 않게 하는 것</b>이라, 담당 마릿수가 늘 함께 나온다(FR-PN13-01).
 */
@Tag(name = "Partner-Assignment",
        description = "[점주] 선생님별 담당 배정과 마릿수 API (PN-13)")
@RestController
@RequestMapping("/api/v1/partner/merchants/{merchantId}/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @Operation(summary = "담당 배정 현황 (PN-13)",
            description = """
                    선생님별 담당 강아지와 마릿수, 아직 담당이 없는 원생을 함께 반환합니다.

                    ★ staff는 담당이 적은 순으로 정렬됩니다 — 다음에 누구에게 맡길지가
                    목록 맨 위에 있어야 하기 때문입니다.
                    unassigned가 비고 부하가 고르면 배정이 끝난 것입니다.

                    배정 대상은 그날 등원한 원생뿐입니다 — 결석한 아이를 배정해 봐야 현장에 없습니다.
                    담당 선생님이 퇴사했다면 그 강아지는 미배정으로 되돌아와 보입니다.""")
    @GetMapping
    public ResponseEntity<ApiResponse<AssignmentResponse.board>> getBoard(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Parameter(description = "조회할 날짜", example = "2026-09-08")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        var response = assignmentService.getBoard(merchantId, principal.getUserId(), date);
        return ResponseEntity.ok(ApiResponse.success("담당 배정 현황 조회 완료", response));
    }

    @Operation(summary = "담당 배정 (PN-13)",
            description = """
                    여러 강아지를 한 선생님에게 한 번에 맡깁니다. 처리 후 갱신된 현황을 그대로 반환하니
                    화면을 다시 그리면 됩니다.

                    ⚠️ 이미 다른 선생님에게 배정된 강아지는 이쪽으로 옮겨집니다 —
                    아침에 배정을 조정하는 것이 정상 작업이고, "이미 배정됨"으로 막으면
                    먼저 해제하는 두 번의 조작이 필요해집니다.

                    staffId는 merchant_staff.id입니다 (users.id가 아닙니다).
                    한 강아지는 하루에 한 선생님만 맡습니다 — 둘로 나누면 사고가 났을 때 책임이 흐려집니다.""")
    @PostMapping
    public ResponseEntity<ApiResponse<AssignmentResponse.board>> assign(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Valid @RequestBody AssignmentRequest.assign request
    ) {
        var response = assignmentService.assign(merchantId, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("담당 배정 완료", response));
    }

    @Operation(summary = "담당 배정 해제 (PN-13)",
            description = "해당 원생들이 미배정으로 돌아갑니다. 갱신된 현황을 반환합니다.")
    @PostMapping("/unassign")
    public ResponseEntity<ApiResponse<AssignmentResponse.board>> unassign(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "매장 ID") @PathVariable UUID merchantId,
            @Valid @RequestBody AssignmentRequest.unassign request
    ) {
        var response = assignmentService.unassign(merchantId, principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("담당 배정 해제 완료", response));
    }
}
