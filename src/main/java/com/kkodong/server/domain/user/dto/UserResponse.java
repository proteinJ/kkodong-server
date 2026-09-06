package com.kkodong.server.domain.user.dto;

import com.kkodong.server.domain.user.domain.Role;
import com.kkodong.server.domain.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        @Schema(description = "회원 ID") UUID id,
        @Schema(description = "이메일", example = "user@example.com") String email,
        @Schema(description = "권한", example = "USER") Role role,
        @Schema(description = "보여질 유저 닉네임", example = "콩이엄마") String displayName,
        @Schema(description = "프로필 이미지 주소") String profileImageUrl,
        @Schema(description = "온보딩 입력완료 시간") OffsetDateTime onboardingCompletedAt,
        @Schema(description = "산책 시간대") List<String> walkTimeSlot
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getDisplayName(),
                user.getProfileImageUrl(),
                user.getOnboardingCompletedAt(),
                user.getWalkTimeSlots()
        );
    }

    public record summary(
            @Schema(description = "회원 ID") UUID id,
            @Schema(description = "닉네임", example = "몽이엄마") String displayName,
            @Schema(description = "프로필 이미지 URL") String profileImageUrl,
            @Schema(description = "산책 시간대") List<String> walkTimeSlot
            ) {
        public static summary from(User user) {
            return new summary(
                    user.getId(),
                    user.getDisplayName(),
                    user.getProfileImageUrl(),
                    user.getWalkTimeSlots()
            );
        }
    }

}
