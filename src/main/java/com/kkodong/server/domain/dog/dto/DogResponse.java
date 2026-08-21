package com.kkodong.server.domain.dog.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

public class DogResponse {

    public record detailInfo(
        @Schema(description = "반려견 ID") UUID id,
        @Schema(description = "보호자(회원) ID") UUID ownerId,
        @Schema(description = "이름", example = "초코") String name,
        @Schema(description = "견종", example = "포메라니안") String breed,
        @Schema(description = "생년월일", example = "2021-05-01") LocalDate birthDate,
        @Schema(description = "성별", example = "MALE") String gender,
        @Schema(description = "크기", example = "SMALL") String size,
        @Schema(description = "활동성", example = "HIGH") String energyLevel,
        @Schema(description = "중성화 여부") Boolean neutered,
        @Schema(description = "프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "동물등록번호", example = "410000012345678") String animalRegistrationNumber
    ) {}

    public record patch(
        @Schema(description = "이름", example = "초코") String name,
        @Schema(description = "견종", example = "포메라니안") String breed,
        @Schema(description = "생년월일", example = "2021-05-01") LocalDate birthDate,
        @Schema(description = "성별", example = "MALE") String gender,
        @Schema(description = "크기", example = "SMALL") String size,
        @Schema(description = "활동성", example = "HIGH") String energyLevel,
        @Schema(description = "중성화 여부") Boolean neutered,
        @Schema(description = "프로필 이미지 URL") String profileImageUrl,
        @Schema(description = "동물등록번호", example = "410000012345678") String animalRegistrationNumber
    ) {}

}
