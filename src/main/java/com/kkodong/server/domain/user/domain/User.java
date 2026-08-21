package com.kkodong.server.domain.user.domain;

import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 꼬동의 users 테이블(V1__init.sql)에 매핑.
 * home_location(V2__add_user_home_location.sql)·subscription_tier는 아직 어떤
 * 엔드포인트도 계약이 정해지지 않아(ONBOARD-2, SUB 미착수) 미매핑 — PostGIS 매핑
 * 방식도 walk_posts 등과 함께 정할 것이라 여기서 먼저 결정하지 않는다.
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

    @Column(name = "apple_user_id", unique = true)
    private String appleUserId; // Sign in with Apple의 sub claim. 이메일 로그인 전용이면 null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "display_name")
    private String displayName; // 가입 시점엔 null, 온보딩에서 채움(API_SPEC.md 0절)

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "onboarding_completed_at")
    private OffsetDateTime onboardingCompletedAt; // null이면 온보딩 미완료(FOUNDATION-2 라우팅 분기 기준)

    // PostGIS geography(Point,4326). 좌표 순서가 (경도 lng, 위도 lat)로 흔한 직관과 반대다
    // — Point 생성 시 new Coordinate(lng, lat) 순서를 항상 확인할 것(V1__init.sql 주석).
    // 원본 좌표는 정밀하게 저장하고, 흐림(반올림)은 API 응답 직렬화 단계에서 처리한다
    // (V2__add_user_home_location.sql 설계 원칙 — 흐린 좌표를 별도 컬럼에 중복 저장하지 않는다).
    @Column(name = "home_location", columnDefinition = "geography(Point,4326)")
    private Point homeLocation; // nullable — 온보딩 전에는 미설정

    public void updatePassword(String password) {
        this.password = password;
    }

    /**
     * PATCH용 부분 수정
     */
    public void updateProfile(UserProfileUpdate req) {
        if (req.displayName() != null) this.displayName = req.displayName();
        if (req.profileImageUrl() != null) this.profileImageUrl = req.profileImageUrl();
        if (req.homeLocation() != null) this.homeLocation = req.homeLocation();
    }

    public void completeOnboarding() {
        if(this.onboardingCompletedAt == null) this.onboardingCompletedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
