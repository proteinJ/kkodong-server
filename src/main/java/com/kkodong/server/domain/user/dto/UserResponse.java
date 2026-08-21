package com.kkodong.server.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record UserResponse(
        @Schema(description = "회원 ID") UUID id,
        @Schema(description = "이메일", example = "user@example.com") String email,
        @Schema(description = "권한", example = "USER") String role
) {
}
