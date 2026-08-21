package com.kkodong.server.domain.dog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class DogRequest {

    /**
     * 성향 태그는 8종 중 최대 3개(빈 배열 허용). 값 유효성은 PersonalityTrait enum이,
     * 개수는 여기 @Size와 DB CHECK 제약이 이중으로 막는다.
     * 누끼 이미지는 POST /dogs/{dogId}/cutout 으로만 들어오므로 등록 body에 없다.
     */
    public record registration(
            @Schema(description = "이름", example = "초코") @NotBlank String name,
            @Schema(description = "견종", example = "포메라니안") String breed,
            @Schema(description = "생년월일", example = "2021-05-01") LocalDate birthDate,
            @Schema(description = "성별", example = "MALE") String gender,
            @Schema(description = "크기", example = "SMALL") String size,
            @Schema(description = "활동성", example = "HIGH") String energyLevel,
            @Schema(description = "중성화 여부", example = "true") Boolean neutered,
            @Schema(description = "동물등록번호", example = "410000012345678") String animalRegistrationNumber,
            @Schema(description = "성향 태그 (8종 중 최대 3개)", example = "[\"활발함\",\"사교적\"]")
            @Size(max = 3, message = "성향 태그는 최대 3개까지 선택할 수 있습니다.")
            List<String> personalityTraits,
            @Schema(description = "몸무게(kg)", example = "5.6") BigDecimal weightKg
    ) {}

    public record patch(
            @Schema(description = "이름", example = "딸기") String name,
            @Schema(description = "견종", example = "비글") String breed,
            @Schema(description = "생년월일", example = "2022-05-01") LocalDate birthDate,
            @Schema(description = "성별", example = "MALE") String gender,
            @Schema(description = "크기", example = "SMALL") String size,
            @Schema(description = "활동성", example = "HIGH") String energyLevel,
            @Schema(description = "중성화 여부", example = "true") Boolean neutered,
            @Schema(description = "동물등록번호", example = "230023012345678") String animalRegistrationNumber,
            @Schema(description = "성향 태그 (8종 중 최대 3개)", example = "[\"차분함\"]")
            @Size(max = 3, message = "성향 태그는 최대 3개까지 선택할 수 있습니다.")
            List<String> personalityTraits,
            @Schema(description = "몸무게(kg)", example = "7.2") BigDecimal weightKg
    ) {}
}
