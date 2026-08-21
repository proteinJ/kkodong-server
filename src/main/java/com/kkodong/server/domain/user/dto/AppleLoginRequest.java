package com.kkodong.server.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AppleLoginRequest(
        @Schema(description = "iOS ASAuthorizationController에서 받은 Apple identity token(JWT)")
        @NotBlank String identityToken
) {
}
