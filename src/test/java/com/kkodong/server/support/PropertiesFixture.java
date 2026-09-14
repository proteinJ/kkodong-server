package com.kkodong.server.support;

import com.kkodong.server.global.config.DogProperties;
import com.kkodong.server.global.config.RecommendationProperties;
import com.kkodong.server.global.config.UserProperties;

import java.util.List;
import java.util.Map;

/**
 * 설정 객체를 손으로 만들어 주는 픽스처.
 *
 * <p>{@code RecommendationScorer}·{@code RecommendationReasonBuilder} 는 생성자 주입이라
 * 스프링 컨텍스트 없이 {@code new} 로 만들 수 있다. 이 둘은 DB를 전혀 읽지 않으므로
 * {@code @SpringBootTest} 를 붙이면 <b>Postgres·Redis 가 떠 있어야만 도는 테스트</b>가
 * 되어 버린다. 점수 계산이 맞는지 보는 데 DB가 필요할 이유가 없다.
 *
 * <p>값은 {@code config/recommendation.yml} 과 같게 맞췄다. 다르면 테스트가 검증하는
 * 것이 실제 동작과 어긋난다. 견종·태그는 테스트에 쓰는 것만 추렸다.
 */
public final class PropertiesFixture {

    private PropertiesFixture() {}

    public static RecommendationProperties recommendation() {
        return new RecommendationProperties(
                5,                          // maxRadiusKm
                List.of(1, 2, 3, 5),        // radiusStepsKm
                300,                        // candidateCap
                0.5,                        // neutralScore
                36,                         // ageToleranceMonths
                new RecommendationProperties.Weights(30, 15, 20, 15, 12, 8, 0, 0),
                new RecommendationProperties.Reason(2, 6, 18, 1, 2)
        );
    }

    public static DogProperties dog() {
        return new DogProperties(
                new DogProperties.Personality(Map.of(
                        "활발함", new DogProperties.TagForms("활발", "활발한"),
                        "사교적", new DogProperties.TagForms("사교적", "사교적인"),
                        "차분함", new DogProperties.TagForms("차분", "차분한")
                )),
                new DogProperties.Breed(Map.of(
                        "small_companion", new DogProperties.Breed.Group("소형 반려견",
                                List.of("말티즈", "푸들", "파피용")),
                        "sporting", new DogProperties.Breed.Group("사냥·스포팅",
                                List.of("비글")),
                        "mixed", new DogProperties.Breed.Group("믹스",
                                List.of("믹스"))
                ))
        );
    }

    public static UserProperties user() {
        return new UserProperties(new UserProperties.WalkTimeSlot(Map.of(
                "dawn", "새벽",
                "morning", "아침",
                "noon", "낮",
                "afternoon", "오후",
                "evening", "저녁",
                "night", "밤"
        )));
    }
}
