package com.kkodong.server.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.locationtech.jts.geom.Point;

import java.util.List;

public record UpdateRequest(
        @Schema(description = "보여질 이름", example = "콩이맘") String displayName,
        @Schema(description = "프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "산책 시간대", example = "[\"morning\",\"evening\"]") @Size(max = 3, message = "산책 시간대는 최대 3개까지 선택할 수 있습니다.")
        List<String> walkTimeSlots,
        @Valid @Schema(description = "자택 위치", example = "위도, 경도") HomeLocation homeLocation
        ) {
        public record HomeLocation(
                @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
                @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng
        ) {}
 }
