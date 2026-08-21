package com.kkodong.server.domain.dog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public class DogRequest {

    public record registration(
        @Schema(description = "이름", example = "초코") @NotBlank String name,
        @Schema(description = "견종", example = "포메라니안") String breed,
        @Schema(description = "생년월일", example = "2021-05-01") LocalDate birthDate,
        @Schema(description = "성별", example = "MALE") String gender,
        @Schema(description = "크기", example = "SMALL") String size,
        @Schema(description = "활동성", example = "HIGH") String energyLevel,
        @Schema(description = "중성화 여부", example = "true") Boolean neutered,
        @Schema(description = "동물등록번호", example = "410000012345678") String animalRegistrationNumber
    ) {}

    public record patch(
            @Schema(description = "이름", example = "딸기") String name,
            @Schema(description = "견종", example = "비글") String breed,
            @Schema(description = "생년월일", example = "2022-05-01") LocalDate birthDate,
            @Schema(description = "성별", example = "MALE") String gender,
            @Schema(description = "크기", example = "SMALL") String size,
            @Schema(description = "활동성", example = "HIGH") String energyLevel,
            @Schema(description = "중성화 여부", example = "true") Boolean neutered,
            @Schema(description = "동물등록번호", example = "230023012345678") String animalRegistrationNumber
    ) {}
}
