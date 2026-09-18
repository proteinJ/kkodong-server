package com.kkodong.server.global.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 값의 출처: {@code ../../resources/config/recommendation.yml}
 *
 * <p>검증은 <b>기동 시점</b>에 돈다. 설정 한 줄이 빠지거나 키 이름이 어긋나면 서버가 아예
 * 뜨지 않는 편이, 잘못된 상태로 떠서 첫 요청에 NPE 를 내는 것보다 낫다. 특히 원시 타입은
 * 바인딩에 실패해도 예외 없이 0 이 되기 때문에, 검증이 없으면 "나이가 비슷해요"가 영원히
 * 안 뜨는 식으로 조용히 망가진다.
 */
// ../../resources/config/recommendation.yml
@ConfigurationProperties(prefix = "kkodong.recommendation")
@Validated
public record RecommendationProperties (
        @Positive int maxRadiusKm,
        @NotEmpty List<Integer> radiusStepsKm,
        @Positive int candidateCap, // 점수를 매길 후보를 몇 마리까지 끌어올 것 인가
        @DecimalMin("0.0") @DecimalMax("1.0") double neutralScore, // 0~1 정규화 점수 자리에 들어간다
        @Positive int ageToleranceMonths,
        @Valid @NotNull Weights weights,
        @Valid @NotNull Reason reason
) {
    /** gender·neutered 는 의도적으로 0(미사용)이라 PositiveOrZero 다. */
    public record Weights(
            @PositiveOrZero int distance,
            @PositiveOrZero int timeSlot,
            @PositiveOrZero int personality,
            @PositiveOrZero int age,
            @PositiveOrZero int size,
            @PositiveOrZero int breed,
            @PositiveOrZero int gender,
            @PositiveOrZero int neutered
    ) {}

    /** 이유 문장의 임계치. 전부 "이 값 이하/이상이면 문구를 쓴다"라 0 이나 음수가 의미 없다. */
    public record Reason(
            @Positive int personalityManyTags,
            @Positive int ageVeryCloseMonths,
            @Positive int ageCloseMonths,
            @Positive double walkDistanceKm,
            @Positive int maxClauses
    ) {}
}
