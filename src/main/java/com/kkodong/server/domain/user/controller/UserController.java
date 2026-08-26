package com.kkodong.server.domain.user.controller;

import com.kkodong.server.domain.user.dto.UserResponse;
import com.kkodong.server.domain.user.dto.PasswordChangeRequest;
import com.kkodong.server.domain.user.dto.UpdateRequest;
import com.kkodong.server.domain.user.service.UserService;
import com.kkodong.server.global.common.ApiResponse;
import com.kkodong.server.global.security.PrincipalDetails;
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

    @Operation(summary = "내 정보 수정", description = "로그인한 회원 본인의 정보를 수정합니다.")
    @PatchMapping("/me")
    public ApiResponse<UserResponse> patchMe(
            @AuthenticationPrincipal PrincipalDetails principal,
            @Valid @RequestBody UpdateRequest request) {
        UserResponse userResponse = userService.updateMyInfo(principal.getUserId(), request);
        return ApiResponse.success("내 정보 수정 완료", userResponse);
    }

    @Operation(summary = "회원 탈퇴", description = "로그인한 회원 본인의 계정을 삭제합니다.")
    @DeleteMapping("/me")
    public ApiResponse<Void> deleteMe(@AuthenticationPrincipal PrincipalDetails principal) {
        userService.deleteMe(principal.getUserId());
        return ApiResponse.success("탈퇴 완료");
    }

    @Operation(summary = "온보딩", description = "온보딩 정보 입력 받기 완료되었는가?")
    @PatchMapping("/me/onboarding")
    public ApiResponse<UserResponse> onboarding(@AuthenticationPrincipal PrincipalDetails principal) {
        UserResponse userResponse = userService.onboarding(principal.getUserId());
        return ApiResponse.success("온보딩 입력 완료", userResponse);
    }
}
