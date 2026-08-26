package com.kkodong.server.domain.safety.controller;

import com.kkodong.server.domain.safety.dto.BlockRequest;
import com.kkodong.server.domain.safety.dto.BlockResponse;
import com.kkodong.server.domain.safety.service.BlockService;
import com.kkodong.server.global.common.ApiResponse;
import com.kkodong.server.global.security.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Block", description = "유저 차단 API")
@RestController
@RequestMapping("/api/v1/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    /**
     * 차단은 <b>멱등</b>이라 이미 차단된 상대를 다시 차단해도 200을 준다.
     * Dog 등록(201)과 다른 이유: 클라이언트 입장에서 중요한 건 "새로 만들어졌는가"가 아니라
     * "차단된 상태인가"이고, 재시도가 409로 실패하면 UI가 불필요하게 에러를 띄워야 한다.
     */
    @Operation(summary = "유저 차단", description = "지정한 유저를 차단합니다. 이미 차단한 상대면 기존 차단을 그대로 반환합니다(멱등).")
    @PostMapping
    public ResponseEntity<ApiResponse<BlockResponse.detailInfo>> block(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Valid @RequestBody BlockRequest.create request
    ) {
        BlockResponse.detailInfo response = blockService.block(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("차단 완료", response));
    }

    /**
     * 해제도 <b>멱등</b>이다. 차단하지 않은 상대를 해제해도 200 — 차단 해제 버튼은 차단
     * 목록 화면에서만 눌리고, 목록이 낡아 없는 대상이 눌렸더라도 사용자가 원한 최종 상태
     * ("차단돼 있지 않음")는 이미 달성돼 있기 때문이다.
     */
    @Operation(summary = "차단 해제", description = "지정한 유저의 차단을 해제합니다. 차단하지 않은 상대여도 200을 반환합니다(멱등).")
    @DeleteMapping("/{blockedId}")
    public ResponseEntity<ApiResponse<Void>> unblock(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Parameter(description = "차단 해제할 유저 ID") @PathVariable UUID blockedId
    ) {
        blockService.unblock(principal.getUserId(), blockedId);
        return ResponseEntity.ok(ApiResponse.success("차단 해제 완료"));
    }

    @Operation(summary = "차단 목록 조회", description = "내가 차단한 유저 목록을 최신순으로 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BlockResponse.listItem>>> blockListGet(
            @AuthenticationPrincipal PrincipalDetails principal
    ) {
        List<BlockResponse.listItem> response = blockService.getBlocks(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success("차단 목록 조회 완료", response));
    }

}
