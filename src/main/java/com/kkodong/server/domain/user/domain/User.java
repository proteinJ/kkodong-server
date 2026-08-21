package com.kkodong.server.domain.user.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 꼬동의 users 테이블(V1__init.sql)에 매핑. 인증에 필요한 최소 필드만 매핑했고,
 * display_name/profile_image_url/subscription_tier/onboarding_completed_at/apple_user_id
 * 등 나머지 컬럼은 꼬동 전용 도메인(FOUNDATION-2, ONBOARD, PET 등) 작업 시 이 엔티티에
 * 이어서 추가할 것 — DB에는 이미 컬럼이 있으니 스키마 변경 없이 필드만 추가하면 됨.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    @Column(name = "id")
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password_hash")
    private String password; // 소셜 로그인 전용 계정이면 null 허용

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    public void updatePassword(String password) {
        this.password = password;
    }
}
