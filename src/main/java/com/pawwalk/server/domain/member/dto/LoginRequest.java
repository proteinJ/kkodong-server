package com.pawwalk.server.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "이메일", example = "user@example.com") @NotBlank @Email String email,
        @Schema(description = "비밀번호", example = "password123") @NotBlank String password
) {
}
