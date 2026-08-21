package com.kkodong.server.domain.dog.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt; // DB DEFAULT now() 사용, 엔티티에서 직접 세팅 안 함

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    public void update(String name, String breed, LocalDate birthDate, Gender gender,
                        DogSize size, EnergyLevel energyLevel, Boolean neutered,
                        String animalRegistrationNumber) {
        this.name = name;
        this.breed = breed;
        this.birthDate = birthDate;
        this.gender = gender;
        this.size = size;
        this.energyLevel = energyLevel;
        this.neutered = neutered;
        this.animalRegistrationNumber = animalRegistrationNumber;
    }

    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * PATCH용 부분 수정 — request에 담기지 않은(null) 필드는 기존 값을 그대로 둔다.
     * update()는 전체 덮어쓰기용이라 PATCH 의미론에는 맞지 않아 별도로 둔다.
     */
    public void patch(String name, String breed, LocalDate birthDate, Gender gender,
                       DogSize size, EnergyLevel energyLevel, Boolean neutered,
                       String animalRegistrationNumber) {
        if (name != null) this.name = name;
        if (breed != null) this.breed = breed;
        if (birthDate != null) this.birthDate = birthDate;
        if (gender != null) this.gender = gender;
        if (size != null) this.size = size;
        if (energyLevel != null) this.energyLevel = energyLevel;
        if (neutered != null) this.neutered = neutered;
        if (animalRegistrationNumber != null) this.animalRegistrationNumber = animalRegistrationNumber;
    }
}
