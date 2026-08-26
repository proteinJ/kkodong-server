package com.kkodong.server.domain.safety.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class BlockRequest {

    public record create(
            @NotNull(message = "차단할 유저 ID는 필수입니다.")
            @Schema(description = "차단할 유저 ID", requiredMode = Schema.RequiredMode.REQUIRED)
            UUID blockedId
    ) {}
}
