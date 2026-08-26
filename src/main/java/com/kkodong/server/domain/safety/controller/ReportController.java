package com.kkodong.server.domain.safety.controller;

import com.kkodong.server.domain.safety.dto.ReportRequest;
import com.kkodong.server.domain.safety.dto.ReportResponse;
import com.kkodong.server.domain.safety.service.ReportService;
import com.kkodong.server.global.common.ApiResponse;
import com.kkodong.server.global.security.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Report", description = "신고 API")
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 차단(멱등, 200)과 달리 신고는 <b>접수될 때마다 새 기록이 남아야</b> 하므로 201이다.
     * 처리 대기 중 중복 신고는 409로 거부한다 — 같은 대상을 반복 신고해 처리 큐를
     * 부풀리는 오남용을 막기 위해서다.
     */
    @Operation(summary = "신고 접수",
            description = "유저를 신고합니다. Phase 1은 유저 신고만 지원하며 커뮤니티 게시글·댓글은 COMMUNITY-1 이후 열립니다. "
                    + "신고는 자동 차단으로 이어지지 않습니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<ReportResponse.detailInfo>> report(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Valid @RequestBody ReportRequest.create request
    ) {
        ReportResponse.detailInfo response = reportService.report(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("신고가 접수되었습니다", response));
    }
}
