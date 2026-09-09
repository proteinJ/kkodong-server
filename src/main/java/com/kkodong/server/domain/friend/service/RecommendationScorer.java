package com.kkodong.server.domain.friend.service;

import com.kkodong.server.domain.dog.domain.DogSize;
import com.kkodong.server.domain.friend.domain.*;
import com.kkodong.server.global.config.DogProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;

import static java.lang.Math.abs;

/**
 * 6개 신호 점수 → 합산 · 동점 셔플
 */
@RequiredArgsConstructor
@Component
public class RecommendationScorer {

    private final DogProperties dogProperties;

    private MatchFacts facts(Subject me, Candidate c) {
        Subject other = c.subject();
        return new MatchFacts(
                c.distanceMeters(),
                shared(me.personalityTraits(), other.personalityTraits()),
                shared(me.walkTimeSlots(), other.walkTimeSlots()),
                ageDiffMonths(me.birthDate(), other.birthDate()),
                sizeStepDiff(me.size(), other.size()),
                breedRelation(me.breed(), other.breed())
        );
    }

    /**
     * 견종 관계 판정. UNPAIRABLE(믹스·기타·미입력)을 <b>가장 먼저</b> 본다 —
     * 뒤로 밀면 둘 다 "믹스"일 때 SAME(1.0)이 되어버린다. 명세는 믹스를 항상 0.3으로 정했다.
     *
     * <p>목록에 없는 견종(other)끼리는 같은 그룹으로 치지 않는다 — "분류가 안 된 견종"이라는
     * 이유로 서로 비슷하다고 볼 근거가 없다. 견종 검증(D005) 이후로는 잘 생기지 않는
     * 경우지만 기존 데이터 대비 방어로 남긴다. mine 만 봐도 되는 이유는, mine 이 other 가
     * 아니면서 theirs 가 other 면 아래 그룹 비교에서 어차피 어긋나기 때문이다.
     */
    private BreedRelation breedRelation(String mine, String theirs) {
        DogProperties.Breed breeds = dogProperties.breed();
        if (breeds.isUnpairable(mine) || breeds.isUnpairable(theirs)) {
            return BreedRelation.UNPAIRABLE;
        } else if (mine.equals(theirs)) {
            return BreedRelation.SAME;
        } else if (breeds.groupOf(mine).equals(DogProperties.Breed.OTHER)) {
            return BreedRelation.DIFFERENT;
        } else if (breeds.groupOf(mine).equals(breeds.groupOf(theirs))) {
            return BreedRelation.SAME_GROUP;
        } else {
            return BreedRelation.DIFFERENT;
        }
    }

    /** 체급 단계 차이 0/1/2. enum 의 compareTo 는 ordinal 차이라 SMALL·MEDIUM·LARGE 선언 순서에 기댄다. */
    private Integer sizeStepDiff(DogSize mine, DogSize theirs) {
        if (mine == null || theirs == null) { return null; }   // 미입력 → 중립(0.5)
        return abs(mine.compareTo(theirs));
    }

    /** 개월 수 차이의 절댓값. abs 를 빼면 순서에 따라 음수가 나와 점수가 1을 넘는다. */
    private Integer ageDiffMonths(LocalDate mine, LocalDate theirs) {
        if (mine == null || theirs == null) { return null; }   // 미입력 → 중립(0.5)
        return Math.abs(Math.toIntExact(ChronoUnit.MONTHS.between(mine, theirs)));
    }

    /**
     * [ 겹치는 항목 ] <b>null</b> = 한쪽이라도 미입력(→ 중립 0.5),
     * <b>[]</b> = 양쪽 다 입력했으나 겹치는 것 없음(→ 0.0).
     * 이 둘을 구분하지 않으면 태그를 건너뛴 신규 유저가 추천 하위에 영구히 깔린다.
     */
    private List<String> shared(List<String> mine, List<String> theirs) {
        if (mine.isEmpty() || theirs.isEmpty()) { return null; }
        return mine.stream().filter(new HashSet<>(theirs)::contains).toList();
    }


    public List<Scored> rankAll(Subject me, List<Candidate> candidates, long shuffleSeed) {
        // 1. 거리 (30점)
        // 2. 시간대 (15점)
        // 3. 성격 (20점)
        // 4. 나이 (15점)
        // 5. 체급 (12점)
        // 6. 견종 (8점)
        return null;
    }
}
