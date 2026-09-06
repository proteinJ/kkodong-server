package com.kkodong.server.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "kkodong.recommendation")
@Validated
public record RecommendationProperties (
        int maxRadiusKm,
        List<Integer> radiusStepsKm,
        int candidateCap, // 점수를 매길 후보를 몇 마리까지 끌어올 것 인가
        double neutralScore,
        int ageToleranceMonths,
        Weights weights
) {
    public record Weights(int distance, int timeSlot, int personality, int age, int size, int breed, int gender, int neutered) {}
}
