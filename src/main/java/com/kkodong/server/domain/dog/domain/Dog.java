package com.kkodong.server.domain.dog.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * V1__init.sql의 dogs 테이블에 매핑. FK(owner_id)는 관계 매핑(@ManyToOne) 없이
 * 단순 UUID 필드로 둔다 — 꼬동은 FK가 많은 프로젝트라 전부 이 방식으로 통일.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "dogs")
public class Dog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId; // users.id FK, 관계 매핑 없이 순수 값만 보관

    @Column(nullable = false)
    private String name;

    private String breed;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    private Gender gender; // GenderConverter가 자동 적용됨(autoApply)

    private DogSize size; // MAP-2 셀프 필터, DogSizeConverter 자동 적용

    @Column(name = "energy_level")
    private EnergyLevel energyLevel; // MAP-2 셀프 필터, EnergyLevelConverter 자동 적용

    private Boolean neutered; // nullable — DB에 NOT NULL 제약 없음

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "animal_registration_number")
    private String animalRegistrationNumber; // 표시용만, API 연동 없음 (QUESTIONS.md Q1)

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "personality_traits", nullable = false)
    @Builder.Default
    private List<String> personalityTraits = new ArrayList<>();

    @Column(name = "cutout_image_url")
    private String cutoutImageUrl;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt; // DB DEFAULT now() 사용, 엔티티에서 직접 세팅 안 함

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * 누끼(배경 제거) 이미지 URL 갱신. 일반 PATCH가 아니라
     * POST /dogs/{dogId}/cutout 전용 값이라 별도 메서드로 둔다(updateProfileImage와 동일 패턴).
     */
    public void updateCutoutImage(String cutoutImageUrl) {
        this.cutoutImageUrl = cutoutImageUrl;
    }

    /**
     * PATCH용 부분 수정 — command의 null 필드는 기존 값을 그대로 둔다.
     *
     * <p>전체 덮어쓰기용 update()가 있었으나 호출처가 없어 삭제했다(2026-08-19).
     * 필드가 늘 때마다 시그니처·호출부·본문 세 곳을 고쳐야 했고, 같은 타입 인자가
     * 연달아 있어 순서를 바꿔 넣어도 컴파일이 통과하는 위험이 있었다.
     */
    public void patch(DogUpdate command) {
        if (command.name() != null) this.name = command.name();
        if (command.breed() != null) this.breed = command.breed();
        if (command.birthDate() != null) this.birthDate = command.birthDate();
        if (command.gender() != null) this.gender = command.gender();
        if (command.size() != null) this.size = command.size();
        if (command.energyLevel() != null) this.energyLevel = command.energyLevel();
        if (command.neutered() != null) this.neutered = command.neutered();
        if (command.animalRegistrationNumber() != null) this.animalRegistrationNumber = command.animalRegistrationNumber();
        if (command.personalityTraits() != null) this.personalityTraits = command.personalityTraits();
        if (command.weightKg() != null) this.weightKg = command.weightKg();
    }
}
