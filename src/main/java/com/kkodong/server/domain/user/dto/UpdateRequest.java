package com.kkodong.server.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.locationtech.jts.geom.Point;

public record UpdateRequest(
        @Schema(description = "보여질 이름", example = "콩이맘") String displayName,
        @Schema(description = "프로필 이미지 URL") String profileImageUrl,
        @Valid @Schema(description = "자택 위치", example = "위도, 경도") HomeLocation homeLocation
        ) {
        public record HomeLocation(
                @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
                @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng
        ) {}
 }
