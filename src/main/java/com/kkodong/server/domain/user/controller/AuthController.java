package com.kkodong.server.domain.user.controller;

import com.kkodong.server.domain.user.domain.TokenDto;
import com.kkodong.server.domain.user.dto.AppleLoginRequest;
import com.kkodong.server.domain.user.dto.LoginRequest;
import com.kkodong.server.domain.user.dto.RefreshRequest;
import com.kkodong.server.domain.user.dto.SignupRequest;
import com.kkodong.server.domain.user.service.AuthService;
import com.kkodong.server.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "회원가입/로그인/토큰 관리 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "이메일/비밀번호로 회원가입합니다. 인증 불필요.")
    @SecurityRequirements
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("회원가입 완료"));
    }

    @Operation(summary = "로그인", description = "이메일/비밀번호로 로그인하여 access/refresh 토큰을 발급받습니다. 인증 불필요.")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenDto>> login(@Valid @RequestBody LoginRequest request) {
        TokenDto tokenDto = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", tokenDto));
    }

    @Operation(summary = "Sign in with Apple", description = "Apple identity token을 검증하여 로그인/가입 처리 후 access/refresh 토큰을 발급받습니다. 인증 불필요.")
    @SecurityRequirements
    @PostMapping("/apple")
    public ResponseEntity<ApiResponse<TokenDto>> loginWithApple(@Valid @RequestBody AppleLoginRequest request) {
        TokenDto tokenDto = authService.loginWithApple(request.identityToken());
        return ResponseEntity.ok(ApiResponse.success("Apple 로그인 성공", tokenDto));
    }

    @Operation(summary = "토큰 재발급", description = "refresh token으로 access/refresh 토큰을 재발급받습니다. 인증 불필요.")
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenDto>> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenDto tokenDto = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success("토큰 재발급 성공", tokenDto));
    }

    @Operation(summary = "로그아웃", description = "access token을 블랙리스트에 등록하고 refresh token을 폐기합니다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody RefreshRequest request) {
        String accessToken = authorizationHeader.replace("Bearer ", "");
        authService.logout(accessToken, request.refreshToken());
        return ResponseEntity.ok(ApiResponse.success("로그아웃 완료"));
    }
}
