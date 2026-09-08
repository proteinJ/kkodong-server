package com.kkodong.server.domain.friend.domain;

import java.util.List;

/**
 * 두 강아지를 비교한 원시 사실. 점수({@code RecommendationScorer})와 이유 문장
 * ({@code RecommendationReasonBuilder})이 각각 여기서 파생된다.
 *
 * <p>점수만 넘기면 이유 문장을 만들 수 없다 — 문장의 조건이 점수가 아니라 원시 값이기
 * 때문이다("겹치는 태그 2개 이상", "나이 6개월 이내"). {@code personality = 1.0} 에서는
 * 몇 개가 겹쳤는지 복원할 수 없다. 그렇다고 문장 쪽에서 교집합을 다시 구하면 같은
 * 로직이 두 곳에 생겨 반드시 어긋난다.
 *
 * <p><b>null 은 "미입력"을 뜻한다.</b> 점수는 중립(0.5)으로 처리하고, 이유 문장은 해당
 * 절을 만들지 않는다. 하나의 사실을 둘이 같은 규칙으로 읽으므로 어긋날 수 없다.
 *
 * @param distanceMeters   DB가 계산한 견주 자택 간 거리(미터). PostGIS {@code ST_Distance}
 * @param sharedTraits     겹친 성향 태그.
 *                         <b>null</b> = 한쪽이라도 태그 미입력 → 중립(0.5) /
 *                         <b>[]</b> = 태그는 있으나 겹치는 것 없음 → 0.0
 * @param sharedTimeSlots  겹친 산책 시간대 키.
 *                         <b>null</b> = 한쪽이라도 미입력 → 중립(0.5) /
 *                         <b>[]</b> = 겹치는 시간대 없음 → 0.0
 * @param ageDiffMonths    개월 수 차이의 절댓값. <b>null</b> = 한쪽이라도 생년월일 미입력 → 중립(0.5)
 * @param sizeStepDiff     체급 단계 차이 — 0(같음) / 1(한 단계) / 2(두 단계).
 *                         <b>null</b> = 한쪽이라도 체급 미입력 → 중립(0.5)
 * @param breed            SAME(1.0) / SAME_GROUP(0.6) / DIFFERENT(0.2) /
 *                         UNPAIRABLE(0.3 — 믹스·기타·미입력)
 */
public record MatchFacts(
        double distanceMeters,
        List<String> sharedTraits,
        List<String> sharedTimeSlots,
        Integer ageDiffMonths,
        Integer sizeStepDiff,
        BreedRelation breed
) {
}
