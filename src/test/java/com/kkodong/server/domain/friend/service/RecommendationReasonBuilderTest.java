package com.kkodong.server.domain.friend.service;

import com.kkodong.server.domain.friend.domain.BreedRelation;
import com.kkodong.server.domain.friend.domain.MatchFacts;
import com.kkodong.server.domain.friend.domain.Subject;
import com.kkodong.server.support.PropertiesFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이유 문장 조립. DB도 스프링도 필요 없다 — {@code MatchFacts} 를 직접 만들어 넣는다.
 *
 * <p>이 테스트가 필요한 이유: 문장이 틀려도 <b>컴파일러도 서버도 아무 말을 하지 않는다.</b>
 * 실제로 조립 결과를 반환하지 않고 {@code null} 을 돌려주는 버그가 있었는데, 빌드는
 * 통과했고 응답을 눈으로 봐야만 드러났다.
 */
class RecommendationReasonBuilderTest {

    private final RecommendationReasonBuilder builder = new RecommendationReasonBuilder(
            PropertiesFixture.dog(), PropertiesFixture.recommendation(), PropertiesFixture.user());

    /** 이유 문장이 쓰는 것은 견종뿐이라 나머지는 비워 둔다. */
    private static Subject other(String breed) {
        return new Subject(null, null, breed, null, null, List.of(), List.of());
    }

    private static MatchFacts facts(double meters, List<String> traits, List<String> slots,
                                    Integer ageDiffMonths, Integer sizeStepDiff, BreedRelation breed) {
        return new MatchFacts(meters, traits, slots, ageDiffMonths, sizeStepDiff, breed);
    }

    @Test
    @DisplayName("절이 2개면 우선순위 순으로 ', ' 로 잇는다")
    void twoClauses() {
        String reason = builder.build(
                facts(300, List.of("활발함", "사교적"), List.of("evening"), null, 0, BreedRelation.SAME_GROUP),
                other("푸들"));

        // 체급(0단계)도 조건을 만족하지만 우선순위에서 밀려 잘린다.
        assertThat(reason).isEqualTo("저녁에 산책하는 것도 같아요, 활발·사교적인 성격이 우리 아이랑 닮았어요");
    }

    @Test
    @DisplayName("겹치는 시간대가 여럿이면 전부 나열한다")
    void multipleTimeSlots() {
        // 2026-09-12 개정 — "저녁에"보다 "저녁·아침에"가 마주칠 확률이 두 배라는 정보를 버리지 않는다.
        String reason = builder.build(
                facts(300, null, List.of("evening", "morning"), null, null, BreedRelation.DIFFERENT),
                other("비글"));

        assertThat(reason).isEqualTo("저녁·아침에 산책하는 것도 같아요");
    }

    @Test
    @DisplayName("겹치는 태그가 1개면 나열형이 아니라 다른 템플릿을 쓴다")
    void singleTrait() {
        // 서로 최대 3개씩 고르는데 1개만 겹친 상황이라 "닮았어요"는 과한 주장이다.
        String reason = builder.build(
                facts(300, List.of("활발함"), null, null, null, BreedRelation.DIFFERENT),
                other("비글"));

        assertThat(reason).isEqualTo("둘 다 활발한 성격이에요");
    }

    @Test
    @DisplayName("조건을 만족하는 절이 3개여도 상위 2개까지만 쓴다")
    void limitedToTwo() {
        // 견종 > 나이 > 체급 순. 견종 절에서 받침 판정(말티즈 → 예요)도 함께 탄다.
        String reason = builder.build(
                facts(300, null, null, 3, 0, BreedRelation.SAME),
                other("말티즈"));

        assertThat(reason).isEqualTo("우리 아이랑 같은 말티즈예요, 나이가 거의 같아요");
    }

    @Test
    @DisplayName("겹치는 신호가 하나도 없으면 거리 문장으로 대체한다")
    void fallbackByDistance() {
        MatchFacts nothingShared = facts(4493, null, null, null, null, BreedRelation.UNPAIRABLE);
        assertThat(builder.build(nothingShared, other("믹스")))
                .isEqualTo("우리 동네 가까이 사는 친구예요");

        MatchFacts walkable = facts(300, null, null, null, null, BreedRelation.UNPAIRABLE);
        assertThat(builder.build(walkable, other("믹스")))
                .isEqualTo("걸어서 만날 수 있는 거리예요");
    }

    @Test
    @DisplayName("절이 1개뿐이어도 거리 문장을 덧붙이지 않는다")
    void fallbackIsReplacementNotSupplement() {
        // 거리는 후보 전원에게 해당돼 정보가 없다. 진짜 이유가 희석된다.
        String reason = builder.build(
                facts(4493, List.of("활발함"), List.of(), null, 1, BreedRelation.DIFFERENT),
                other("비글"));

        assertThat(reason).isEqualTo("둘 다 활발한 성격이에요");
    }

    @Test
    @DisplayName("나이 임계치 경계")
    void ageBoundaries() {
        assertThat(clauseFor(facts(300, null, null, 6, null, BreedRelation.DIFFERENT)))
                .isEqualTo("나이가 거의 같아요");
        assertThat(clauseFor(facts(300, null, null, 7, null, BreedRelation.DIFFERENT)))
                .isEqualTo("나이가 비슷해요");
        assertThat(clauseFor(facts(300, null, null, 18, null, BreedRelation.DIFFERENT)))
                .isEqualTo("나이가 비슷해요");
        // 19개월부터는 절을 만들지 않아 거리 fallback 으로 떨어진다.
        assertThat(clauseFor(facts(300, null, null, 19, null, BreedRelation.DIFFERENT)))
                .isEqualTo("걸어서 만날 수 있는 거리예요");
    }

    @Test
    @DisplayName("미입력(null)은 절을 만들지 않는다 — 점수의 중립 0.5 와 다른 처리다")
    void missingDataMakesNoClause() {
        // 시루(seed-5) 처럼 성향·체급·시간대가 전부 비어 있어도 예외 없이 문장이 나와야 한다.
        String reason = builder.build(
                facts(4493, null, null, null, null, BreedRelation.UNPAIRABLE),
                other("믹스"));

        assertThat(reason).isNotBlank();
    }

    private String clauseFor(MatchFacts f) {
        return builder.build(f, other("비글"));
    }
}
