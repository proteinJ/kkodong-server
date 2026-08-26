package com.kkodong.server.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @Schema(description = "현재 비밀번호", example = "password123") @NotBlank String currentPassword,
        @Schema(description = "새 비밀번호 (8자 이상)", example = "newPassword123") @NotBlank @Size(min = 8) String newPassword
) {
}
