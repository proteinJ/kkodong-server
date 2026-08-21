package com.pawwalk.server.domain.user.controller;

import com.pawwalk.server.domain.user.dto.UserResponse;
import com.pawwalk.server.domain.user.dto.PasswordChangeRequest;
import com.pawwalk.server.domain.user.service.UserService;
import com.pawwalk.server.global.common.ApiResponse;
import com.pawwalk.server.global.security.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "회원 정보 조회/수정/탈퇴 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회", description = "로그인한 회원 본인의 정보를 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal PrincipalDetails principal) {
        return ApiResponse.success("조회 성공", userService.getMe(principal.getUserId()));
    }

    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다.")
    @PatchMapping("/me/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(principal.getUserId(), request);
        return ApiResponse.success("비밀번호 변경 완료");
    }

    /**
     * 회원 탈퇴. Apple App Store 심사 가이드라인 5.1.1(v) — 가입 기능이 있는 앱은
     * 앱 내 계정 삭제 기능이 필수라, iOS 클라이언트를 붙일 프로젝트라면 반드시 유지할 것.
     */
    @Operation(summary = "회원 탈퇴", description = "로그인한 회원 본인의 계정을 삭제합니다.")
    @DeleteMapping("/me")
    public ApiResponse<Void> deleteMe(@AuthenticationPrincipal PrincipalDetails principal) {
        userService.deleteMe(principal.getUserId());
        return ApiResponse.success("탈퇴 완료");
    }
}
